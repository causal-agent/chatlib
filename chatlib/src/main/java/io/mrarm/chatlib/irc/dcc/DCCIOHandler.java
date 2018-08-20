package io.mrarm.chatlib.irc.dcc;

import java.io.IOException;
import java.nio.channels.*;

public class DCCIOHandler extends Thread {

    private static final DCCIOHandler instanceSingleton = new DCCIOHandler();

    public static DCCIOHandler getInstance() {
        return instanceSingleton;
    }


    private Selector selector;
    private final Object selectorLock = new Object();
    private boolean running = false;

    public DCCIOHandler() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    SelectionKey addServer(DCCServer server, ServerSocketChannel channel) throws ClosedChannelException {
        SelectionKey ret;
        synchronized (selectorLock) {
            selector.wakeup();
            ret = channel.register(selector, SelectionKey.OP_ACCEPT, server);
        }
        startIfNeeded();
        return ret;
    }

    SelectionKey addUploadSession(DCCServer.UploadSession session) throws ClosedChannelException {
        SelectionKey ret;
        synchronized (selectorLock) {
            selector.wakeup();
            ret = session.socket.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, session);
        }
        startIfNeeded();
        return ret;
    }

    public void unregister(SelectionKey key) {
        synchronized (selectorLock) {
            selector.wakeup();
            key.cancel();
        }
    }

    private void handleSelectionKey(SelectionKey k) throws IOException {
        if (k.isAcceptable())
            ((DCCServer) k.attachment()).doAccept();

        if (k.attachment() instanceof DCCServer.UploadSession) {
            if ((k.readyOps() & SelectionKey.OP_READ) != 0)
                ((DCCServer.UploadSession) k.attachment()).doRead();
            if (!k.isValid()) {
                ((DCCServer.UploadSession) k.attachment()).close();
                return;
            }
            if (k.isWritable())
                ((DCCServer.UploadSession) k.attachment()).doWrite();
        }
    }

    @Override
    public void run() {
        while (true) {
            synchronized (selectorLock) {
                if (selector.keys().size() <= 0) {
                    running = false;
                    break;
                }
            }
            try {
                selector.select();
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (SelectionKey k : selector.selectedKeys()) {
                try {
                    handleSelectionKey(k);
                } catch (IOException err) {
                    err.printStackTrace();
                }
            }
        }
    }



    void startIfNeeded() {
        synchronized (selectorLock) {
            if (running)
                return;
            running = true;
        }
        while (isAlive()) { // wait until it stops if it's running
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }
        start();
    }

}
