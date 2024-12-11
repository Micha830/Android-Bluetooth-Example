package com.michaswdev.bluetoothconnector;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


public class ConnectionHandling {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice conDevice;
    private boolean isConnected = false;
    private boolean isDisconnectByUser = false;
    private boolean isConnecting = false;
    private boolean isReconnecting = false;

    private BluetoothListener listener;

    private Handler handler = new Handler();

    public ConnectionHandling(BluetoothListener listener) {
         this.listener = listener;
    }


    public interface BluetoothListener {
        void onStatusConnection(String status);
        void onConnected(BluetoothDevice device);
        void onDisconnected(Boolean state);
        void onConnectionFailed(String message);
        void onDataReceived(String data);
    }

    public void connect(BluetoothDevice device) {

        closeSocket();

        new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothSocket.connect();

                isDisconnectByUser = false;

                isConnected = true;
                listener.onDisconnected(false);
                conDevice = device;
                receiveData();//start erhalt von Daten

                if (listener != null) {
                    listener.onConnected(device);
                }
            } catch (IOException e) {
                isConnected = false;
                closeSocket();
                if (listener != null) {
                    listener.onConnectionFailed(e.getMessage());
                }
            }
        }).start();

    }

    public void disconnect() {
        listener.onStatusConnection("disconnect: " + bluetoothSocket.isConnected());
        isDisconnectByUser = true;

        if(bluetoothSocket != null){
            closeSocket();
            isConnected = false;
            conDevice = null;
        }
        else{
            listener.onStatusConnection("Error disconnecting ");
        }

        if (listener != null) {
            listener.onDisconnected(isConnected);
        }

    }

    public void reconnect(BluetoothDevice device) {

        if (device != null && device.getBondState() == BluetoothDevice.BOND_BONDED && !isDisconnectByUser) {
            //  Log.d(TAG, "Attempting to reconnect to: " + pairedDevice.getName());

            isReconnecting = true;

            // Reconnection process
            new Thread(() -> {
                try {

                    closeSocket();
                    //bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                    bluetoothSocket = conDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                    bluetoothSocket.connect();
                    receiveData();

                    listener.onStatusConnection("Reconnect: " + device.getName());

                    // if succesfully
                    isReconnecting = false;

                } catch (Exception e) {
                    //   Log.d(TAG, "Reconnection failed, will retry.", e);
                    //if not succesfully repeat
                    listener.onStatusConnection("Device lost, retrying to reconnect: " + device.getName());
                    handler.postDelayed(() -> reconnect(device), 5000);
                }
            }).start();
        }
    }


    private void closeSocket() {
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
                listener.onStatusConnection(" close : " + bluetoothSocket.isConnected());
                Log.d("Bluetooth", "Socket geschlossen.");
            } catch (IOException e) {
                Log.e("Bluetooth", "Fehler beim Schließen des Sockets: " + e.getMessage());
                e.printStackTrace();
                listener.onStatusConnection("error close : " + bluetoothSocket.isConnected());
            }finally {
                bluetoothSocket = null;
            }
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isConnecting() {
        return isConnecting;
    }

    public BluetoothDevice getConnectedDevice() {
        return conDevice;
    }

    /************************Data handling*************************************/
    public void sendData(String data) {
        new Thread(() -> {
            try {
                OutputStream outputStream = bluetoothSocket.getOutputStream();
                outputStream.write(data.getBytes());
                Log.d("MainActivity", "Data sent: " + data);
            } catch (IOException e) {
                Log.e("MainActivity", "Failed to send data", e);
            }
        }).start();
    }

    public void receiveData() {
        new Thread(() -> {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                byte[] buffer = new byte[1024];
                int bytes;

                while (bluetoothSocket.isConnected()) {
                    bytes = inputStream.read(buffer);

                    if (bytes > 0) {
                        String received = new String(buffer, 0, bytes);
                        listener.onDataReceived(received);
                    }
                }
            } catch (IOException e) {
                Log.e("MainActivity", "Failed to receive data", e);
            }
        }).start();
    }
}
