package io.mrarm.chatlib.irc.dcc;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class DCCClient implements Closeable {

    private ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
    private ByteBuffer ackBuffer = ByteBuffer.allocateDirect(4);

    private SocketChannel socket;
    private FileChannel file;
    private SelectionKey selectionKey;
    private long totalSize;

    public DCCClient(SocketChannel socket, FileChannel file, long offset) throws IOException {
        this.socket = socket;
        this.file = file;
        this.socket.configureBlocking(false);
        this.file.position(offset);
        this.totalSize = offset;
        selectionKey = DCCIOHandler.getInstance().register(socket, SelectionKey.OP_READ, (SelectionKey k) -> {
            if (k.isReadable())
                onRead();
        });
    }

    @Override
    public void close() {
        if (selectionKey != null)
            selectionKey.cancel();
        selectionKey = null;
        try {
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        socket = null;
        try {
            if (file != null)
                file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        file = null;
    }

    private int readSocket(ByteBuffer buffer) {
        if (socket == null)
            return -1;
        int r = -1;
        try {
            r = socket.read(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (r < 0) {
            close();
        } else {
            totalSize += r;
        }
        return r;
    }

    private void onRead() throws IOException {
        while (readSocket(buffer) > 0 || buffer.position() > 0) {
            buffer.flip();
            try {
                file.write(buffer);
            } finally {
                buffer.compact();
            }
        }
        ackBuffer.putInt((int) totalSize);
        socket.write(ackBuffer);
    }
}
