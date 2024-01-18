package com.example.chat;

import java.io.FileOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import android.os.Environment;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    static final int SocketServerPORT = 8080; // se define el puerto en el que se establecerá el servidor socket.
    TextView infoIp, infoPort, chatMsg;
    String msgLog = "";
    List<ChatClient> userList;
    ServerSocket serverSocket;

    //Este método se ejecuta cuando se crea la actividad. En este método se inicializan las vistas y se establece la dirección IP del dispositivo en el TextView
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        infoIp = (TextView) findViewById(R.id.infoip);
        infoPort = (TextView) findViewById(R.id.infoport);
        chatMsg = (TextView) findViewById(R.id.chatmsg);

        infoIp.setText(getIpAddress());
        userList = new ArrayList<ChatClient>();

        ChatServerThread chatServerThread = new ChatServerThread();
        chatServerThread.start();//iniciar servidor a traves de socket
    }

    @Override
    protected void onDestroy() {// este metodo es para cerrar el servidor socket
        super.onDestroy();

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ChatServerThread extends Thread {// Esta clase interna hereda de Thread y se utiliza para crear y administrar el servidor socket

        @Override
        public void run() {// Este metodo Se encarga de aceptar conexiones de clientes y crear hilos para manejar cada conexion entrante.
            Socket socket = null;

            try {
                serverSocket = new ServerSocket(SocketServerPORT);
                MainActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        infoPort.setText("Puerto Asignado: "
                                + serverSocket.getLocalPort());
                    }
                });

                while (true) {
                    socket = serverSocket.accept();
                    ChatClient client = new ChatClient();
                    userList.add(client);
                    ConnectThread connectThread = new ConnectThread(client, socket);
                    connectThread.start();
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }

    }

    private class ConnectThread extends Thread {//Esta clase interna hereda de Thread y se utiliza para manejar una conexión de cliente específica.
        Socket socket;
        ChatClient connectClient;
        String msgToSend = "";

        ConnectThread(ChatClient client, Socket socket){
            connectClient = client;
            this.socket= socket;
            client.socket = socket;
            client.chatThread = this;
        }

        @Override
        public void run() {//Este metodo se encarga de recibir y enviar mensajes a través del socket, tambien el manejo de imagenes enviadas por el cliente.
            DataInputStream dataInputStream = null;
            DataOutputStream dataOutputStream = null;

            try {
                dataInputStream = new DataInputStream(socket.getInputStream());
                dataOutputStream = new DataOutputStream(socket.getOutputStream());

                String n = dataInputStream.readUTF();

                connectClient.name = n;

                msgLog += connectClient.name + " Usuario conectado @"
                        + connectClient.socket.getInetAddress()
                        + ":" + connectClient.socket.getPort() + "\n";
                MainActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        chatMsg.setText(msgLog);
                    }
                });

                dataOutputStream.writeUTF("Bienvenido: " + n + "\n");
                dataOutputStream.flush();

                broadcastMsg(n + " Chat Activo.\n");

                while (true) {
                    if (dataInputStream.available() > 0) {
                        String newMsg = dataInputStream.readUTF();

                        if (newMsg.equals("IMAGE")) {
                            int imageSize = dataInputStream.readInt();
                            byte[] imageBytes = new byte[imageSize];
                            dataInputStream.readFully(imageBytes, 0, imageSize);

                            // Aquí guardo las imagenes en el disposito movil android
                            // tambien aqui se podria manejar la parte de visualizar la imagen en el servidor o enviarsela a otro cliente cocnetado

                            String filename = "imagen_" + System.currentTimeMillis() + ".jpg";

                            // Ruta de la carpeta donde se guardarán las imágenes en el servidor
                            String savePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/";

                            // Escribiendo los bytes de la imagen en el archivo
                            FileOutputStream fileOutputStream = new FileOutputStream(savePath + filename);
                            fileOutputStream.write(imageBytes);
                            fileOutputStream.close();

                            // Envía un mensaje de confirmación si es necesario
                            sendMsg("Image received");
                        } else {
                            msgLog += n + ": " + newMsg;
                            MainActivity.this.runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    chatMsg.setText(msgLog);
                                }
                            });

                            broadcastMsg(n + ": " + newMsg);
                        }
                    }

                    if(!msgToSend.equals("")){
                        dataOutputStream.writeUTF(msgToSend);
                        dataOutputStream.flush();
                        msgToSend = "";
                    }

                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (dataInputStream != null) {
                    try {
                        dataInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (dataOutputStream != null) {
                    try {
                        dataOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                userList.remove(connectClient);
                MainActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                connectClient.name + " removido.", Toast.LENGTH_LONG).show();

                        msgLog += "-- " + connectClient.name + " se ha ido";
                        MainActivity.this.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                chatMsg.setText(msgLog);
                            }
                        });

                        broadcastMsg("-- " + connectClient.name + " se ha ido");
                    }
                });
            }

        }

        private void sendMsg(String msg){
            msgToSend = msg;
        }

    }

    private void broadcastMsg(String msg){ //Este método se utiliza para enviar un mensaje a todos los clientes conectados.
        for(int i=0; i<userList.size(); i++){
            userList.get(i).chatThread.sendMsg(msg);
            msgLog += "-Enviado-Mensaje-Recibido por " + userList.get(i).name + "\n";
        }

        MainActivity.this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                chatMsg.setText(msgLog);
            }
        });
    }

    //Este método se utiliza para obtener la dirección IP, tambien obtiene las interfaces de red del disppsitivo o los dispositivos y luego itera sobre ellas para obtener las dirreciones Ip de cada uno.
    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += "Dirección IP: "
                                + inetAddress.getHostAddress() + "\n";
                    }

                }

            }

        } catch (SocketException e) {
            e.printStackTrace();
            ip += "Algo ha fallado: " + e.toString() + "\n";
        }
        return ip;
    }
    class ChatClient {
        String name;
        Socket socket;
        ConnectThread chatThread;
    }
}