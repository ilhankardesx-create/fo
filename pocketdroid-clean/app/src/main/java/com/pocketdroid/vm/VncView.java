package com.pocketdroid.vm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class VncView extends View {
    public interface Listener {
        void onConnected(int width, int height, String name);
        void onMessage(String message);
        void onDisconnected(String reason);
    }

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap bitmap;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean running;
    private Listener listener;
    private int fbWidth;
    private int fbHeight;

    public VncView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void connectWithRetry(final String host, final int port) {
        if (running) return;
        running = true;
        new Thread(() -> {
            String last = "VNC bekleniyor";
            for (int attempt = 1; running && attempt <= 120; attempt++) {
                try {
                    connectOnce(host, port);
                    readLoop();
                    return;
                } catch (Exception ex) {
                    last = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                    if (attempt % 10 == 1) notifyMessage("Android ekranı bekleniyor… (" + attempt + ")");
                    closeSocket();
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
            if (running) notifyDisconnected(last);
            running = false;
        }, "PocketDroid-VNC").start();
    }

    public void disconnect() {
        running = false;
        closeSocket();
    }

    private void connectOnce(String host, int port) throws Exception {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 1200);
        socket.setTcpNoDelay(true);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 256 * 1024));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 256 * 1024));

        byte[] version = new byte[12];
        in.readFully(version);
        String serverVersion = new String(version, StandardCharsets.US_ASCII);
        if (!serverVersion.startsWith("RFB 003.")) throw new IOException("Geçersiz VNC sürümü");
        out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();

        int count = in.readUnsignedByte();
        if (count == 0) {
            int len = in.readInt();
            byte[] reason = new byte[Math.max(0, Math.min(len, 4096))];
            in.readFully(reason);
            throw new IOException("VNC güvenlik hatası: " + new String(reason, StandardCharsets.UTF_8));
        }
        boolean none = false;
        for (int i = 0; i < count; i++) {
            if (in.readUnsignedByte() == 1) none = true;
        }
        if (!none) throw new IOException("VNC 'None' güvenliği sunmuyor");
        out.writeByte(1);
        out.flush();
        int securityResult = in.readInt();
        if (securityResult != 0) throw new IOException("VNC erişimi reddedildi: " + securityResult);

        out.writeByte(1);
        out.flush();

        fbWidth = in.readUnsignedShort();
        fbHeight = in.readUnsignedShort();
        byte[] serverPixelFormat = new byte[16];
        in.readFully(serverPixelFormat);
        int nameLen = in.readInt();
        if (nameLen < 0 || nameLen > 1024 * 1024) throw new IOException("VNC isim uzunluğu geçersiz");
        byte[] nameBytes = new byte[nameLen];
        in.readFully(nameBytes);
        String name = new String(nameBytes, StandardCharsets.UTF_8);

        bitmap = Bitmap.createBitmap(fbWidth, fbHeight, Bitmap.Config.ARGB_8888);
        sendPixelFormat();
        sendEncodings();
        requestUpdate(false);
        postInvalidate();
        if (listener != null) post(() -> listener.onConnected(fbWidth, fbHeight, name));
    }

    private synchronized void sendPixelFormat() throws IOException {
        out.writeByte(0);
        out.write(new byte[]{0, 0, 0});
        out.writeByte(32);
        out.writeByte(24);
        out.writeByte(1);
        out.writeByte(1);
        out.writeShort(255);
        out.writeShort(255);
        out.writeShort(255);
        out.writeByte(16);
        out.writeByte(8);
        out.writeByte(0);
        out.write(new byte[]{0, 0, 0});
        out.flush();
    }

    private synchronized void sendEncodings() throws IOException {
        out.writeByte(2);
        out.writeByte(0);
        out.writeShort(1);
        out.writeInt(0);
        out.flush();
    }

    private synchronized void requestUpdate(boolean incremental) throws IOException {
        if (out == null) return;
        out.writeByte(3);
        out.writeByte(incremental ? 1 : 0);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(fbWidth);
        out.writeShort(fbHeight);
        out.flush();
    }

    private void readLoop() throws Exception {
        while (running) {
            int type;
            try {
                type = in.readUnsignedByte();
            } catch (EOFException eof) {
                throw new IOException("VNC bağlantısı kapandı");
            }
            if (type == 0) {
                readFramebufferUpdate();
            } else if (type == 2) {
                // Bell.
            } else if (type == 3) {
                in.read(new byte[3]);
                int len = in.readInt();
                if (len < 0 || len > 16 * 1024 * 1024) throw new IOException("VNC pano verisi çok büyük");
                byte[] skip = new byte[len];
                in.readFully(skip);
            } else {
                throw new IOException("Desteklenmeyen VNC mesajı: " + type);
            }
        }
    }

    private void readFramebufferUpdate() throws Exception {
        in.readUnsignedByte();
        int rects = in.readUnsignedShort();
        for (int r = 0; r < rects; r++) {
            int x = in.readUnsignedShort();
            int y = in.readUnsignedShort();
            int w = in.readUnsignedShort();
            int h = in.readUnsignedShort();
            int encoding = in.readInt();
            if (encoding != 0) throw new IOException("Desteklenmeyen VNC encoding: " + encoding);
            int pixels = w * h;
            byte[] raw = new byte[pixels * 4];
            in.readFully(raw);
            int[] colors = new int[pixels];
            int p = 0;
            for (int i = 0; i < pixels; i++) {
                int b = raw[p++] & 0xff;
                int g = raw[p++] & 0xff;
                int red = raw[p++] & 0xff;
                p++;
                colors[i] = Color.rgb(red, g, b);
            }
            Bitmap bmap = bitmap;
            if (bmap != null) {
                synchronized (this) {
                    bmap.setPixels(colors, 0, w, x, y, w, h);
                }
            }
        }
        postInvalidate();
        requestUpdate(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap bmap = bitmap;
        if (bmap == null) return;
        float scale = Math.min(getWidth() / (float) bmap.getWidth(), getHeight() / (float) bmap.getHeight());
        float w = bmap.getWidth() * scale;
        float h = bmap.getHeight() * scale;
        float left = (getWidth() - w) / 2f;
        float top = (getHeight() - h) / 2f;
        synchronized (this) {
            canvas.drawBitmap(bmap, null, new RectF(left, top, left + w, top + h), paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null || out == null) return true;
        float scale = Math.min(getWidth() / (float) fbWidth, getHeight() / (float) fbHeight);
        float left = (getWidth() - fbWidth * scale) / 2f;
        float top = (getHeight() - fbHeight * scale) / 2f;
        int x = Math.max(0, Math.min(fbWidth - 1, Math.round((event.getX() - left) / scale)));
        int y = Math.max(0, Math.min(fbHeight - 1, Math.round((event.getY() - top) / scale)));
        int mask = event.getActionMasked() == MotionEvent.ACTION_UP ? 0 : 1;
        try {
            sendPointer(mask, x, y);
        } catch (IOException ignored) {}
        return true;
    }

    private synchronized void sendPointer(int mask, int x, int y) throws IOException {
        if (out == null) return;
        out.writeByte(5);
        out.writeByte(mask);
        out.writeShort(x);
        out.writeShort(y);
        out.flush();
    }

    private void closeSocket() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null;
        in = null;
        out = null;
    }

    private void notifyMessage(String message) {
        if (listener != null) post(() -> listener.onMessage(message));
    }

    private void notifyDisconnected(String reason) {
        if (listener != null) post(() -> listener.onDisconnected(reason));
    }
}
