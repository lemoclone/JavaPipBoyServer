package de.luckydonald.pipboyserver.PipBoyServer;

import de.luckydonald.pipboyserver.Constants;
import de.luckydonald.pipboyserver.Messages.ConnectionAccepted;
import de.luckydonald.pipboyserver.Messages.DataUpdate;
import de.luckydonald.pipboyserver.Messages.IDataUpdateListener;
import de.luckydonald.pipboyserver.Messages.KeepAlive;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by luckydonald on 14.01.16.
 */
public class Server {
    public static final int MAX_CLIENTS = 10;
    ServerSocket socket;
    Database db;
    boolean quit = false;
    ExecutorService threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
    public void run() {
        db = new Database();
        db.fillWithDefault();
        db.startCLI();
        while (!this.quit) {
            try {
                ServerSocket socket = new ServerSocket(Constants.CONNECT_TCP_PORT);
                while (!this.quit) {
                    System.out.println("Waiting for game to connect.");
                    Socket acceptedSocket = socket.accept();
                    Runnable session = new Session(acceptedSocket, db);
                    threadPool.submit(session);
                    System.out.println("Started Game session.");
                    //System.out.println(db.toSimpleString());
                }
            } catch (IOException e) {
                socket = null;
                e.printStackTrace();
                try {
                    Thread.sleep(999);
                } catch (InterruptedException ignored) {}
            }
        }
    }
}
class Session implements Runnable, IDataUpdateListener {
    private final Database db;
    private final Socket socket;
    private final ConcurrentLinkedQueue<DataUpdate> updates;
    boolean quit = false;

    public Session(Socket socket, Database db) {
        this.socket = socket;
        this.db = db;
        this.updates = new ConcurrentLinkedQueue<DataUpdate>();
        this.db.registerDataUpdateListener(this);
    }

    @Override
    public void run() {
        KeepAliveThread heartbeat = null;
        Thread heartbeatThread = null;
        System.out.println("Started session.");
        try {
            OutputStream stream = socket.getOutputStream();
            InputStream inStream = socket.getInputStream();
            heartbeat = new KeepAliveThread(socket);
            stream.write(new ConnectionAccepted().toBytes());
            stream.flush();
            heartbeatThread = new Thread(heartbeat, "Heartbeat");
            heartbeatThread.start();
            while (!quit) {
                System.out.println("Checking for updates.");
                DataUpdate update = this.updates.poll();
                if (update != null) {
                    System.out.println("Sending update.");
                    stream.write(update.toBytes());
                    stream.flush();
                    System.out.println("Send update: " + update.toString().substring(0, Math.min(1000, update.toString().length())));
                }
                if (!heartbeatThread.isAlive()) {
                    System.out.println("Heartbeat thread is dead.");
                    break;
                }
                try {
                    Thread.sleep(999);
                } catch (InterruptedException ignore) {
                    quit = true;
                }
            }
        } catch (IOException e) {
            if (heartbeat != null) {
                heartbeat.quit = true;
            }
            if (heartbeatThread != null) {
                try {
                    heartbeatThread.join(1000);
                } catch (InterruptedException ignore) {
                }
            }
            e.printStackTrace();
        }
        System.out.println("Shuting down update thread.");
    }

    @Override
    public void onDataUpdate(DataUpdate update) {
        this.updates.add(update);
    }

    @Override
    public String toString() {
        return "Session{" +
                "socket=" + socket +
                ", updates=" + updates +
                ", quit=" + quit +
                '}';
    }
    public String toStringWithDB() {
        return "Session{" +
                "db=" + db +
                ", socket=" + socket +
                ", updates=" + updates +
                ", quit=" + quit +
                '}';
    }
}
class KeepAliveThread implements Runnable{
    Socket socket;
    boolean quit = false;
    public KeepAliveThread(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run(){
        try {
            OutputStream stream = this.socket.getOutputStream();
            InputStream in = this.socket.getInputStream();
            while (!quit) {
                byte[] bytesToSend = new KeepAlive().toBytes();
                byte[] bytesToRead = new byte[bytesToSend.length];
                stream.write(bytesToSend);
                stream.flush();
                int left = bytesToSend.length;
                int read = 0;
                while (read < left) {
                    int newlyRead = in.read(bytesToRead, 0, bytesToRead.length);
                    if (newlyRead == -1) {
                        System.out.println("Got " + read + " of " + left + " bytes.");
                        //throw new IOException("Möööp! Left: "+left);
                    }
                    read += newlyRead;
                }
                //TODO: read answer.
                try {
                    Thread.sleep(999);
                } catch (InterruptedException ignore) {
                    quit = true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Heartbeat thread done.");
        return;
    }
}