package io.mrarm.chatlib.irc.dcc;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class DCCServer implements Closeable {

    private File file;
    private FileDescriptor fileDescriptor;
    private ServerSocketChannel serverSocket;
    private int socketLimit;
    private List<UploadSession> sessions = new ArrayList<>();

    public DCCServer(File file, int socketLimit) {
        this.file = file;
        this.socketLimit = socketLimit;
    }

    public DCCServer(FileDescriptor fd, int socketLimit) {
        this.fileDescriptor = fd;
        this.socketLimit = socketLimit;
    }

    public int createServerSocket() throws IOException {
        if (serverSocket == null) {
            serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(0));
            serverSocket.configureBlocking(false);
            DCCIOHandler.getInstance().addServer(this, serverSocket);
        }
        return serverSocket.socket().getLocalPort();
    }

    public int getPort() {
        if (serverSocket == null)
            return -1;
        return serverSocket.socket().getLocalPort();
    }

    @Override
    public void close() throws IOException {
        if (serverSocket != null)
            serverSocket.close();
        for (UploadSession session : sessions)
            session.close();
    }

    void doAccept() throws IOException {
        if (socketLimit != -1 && sessions.size() >= socketLimit)
            return;
        SocketChannel socket = serverSocket.accept();
        if (socket == null)
            return;
        System.out.println("Accepted DCC connection from: " + socket.getRemoteAddress().toString());
        new UploadSession(openInputStream(), socket);
    }

    private FileChannel openInputStream() throws IOException {
        if (file != null)
            return new FileInputStream(file).getChannel();
        else
            return new FileInputStream(fileDescriptor).getChannel();
    }


    class UploadSession implements Closeable {

        private ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
        private ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        private FileChannel file;
        private SelectionKey selectionKey;
        SocketChannel socket;

        UploadSession(FileChannel file, SocketChannel socket) throws IOException {
            try {
                this.file = file;
                this.socket = socket;
                socket.configureBlocking(false);
                selectionKey = DCCIOHandler.getInstance().addUploadSession(this);
                sessions.add(this);
            } catch (IOException e) {
                close();
                throw e;
            }
        }

        @Override
        public void close() {
            sessions.remove(this);
            if (selectionKey != null)
                selectionKey.cancel();
            try {
                if (socket != null)
                    socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
            try {
                if (file != null)
                    file.close();
            } catch (IOException ignored) {
            }
            file = null;
        }

        void doRead() throws IOException {
            while (socket.read(readBuffer) > 0); // ignore all the input
        }

        void doWrite() throws IOException {
            int r = 0;
            while ((file != null && (r = file.read(buffer)) > 0) || buffer.position() > 0) {
                buffer.flip();
                socket.write(buffer);
                buffer.compact();
            }
            if (r < 0) {
                try {
                    if (file != null)
                        file.close();
                } catch (IOException ignored) {
                }
                file = null;
            }
        }

    }

}
