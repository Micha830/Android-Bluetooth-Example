package com.michaswdev.bluetoothconnector;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.companion.AssociatedDevice;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.UUID;
import java.util.concurrent.Executor;



public class MainActivity extends AppCompatActivity {

    TextView statusText, receiveText;

    private CompanionDeviceManager companionDeviceManager;
    private ConnectionHandling connectionHandling;

    BroadcastReceiver pairingReceiver;

    private static final int SELECT_DEVICE_REQUEST_CODE = 0;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private boolean isDiscconnected = false; // Flag, um zu verhindern, dass die Wiederverbindung mehrmals gleichzeitig versucht wird

    private ActivityResultLauncher<String> permissionLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        final Button btn_1 = findViewById(R.id.button_1);
        final Button btn_2 = findViewById(R.id.button_2);

        statusText = findViewById(R.id.textView_status);
        receiveText = findViewById(R.id.textView_getMessage);

        btn_1.setOnClickListener(v -> {connectionHandling.sendData(getString(R.string.btn_str1));});
        btn_2.setOnClickListener(v -> {connectionHandling.sendData( getString(R.string.btn_str2));});


        connectionHandling = new ConnectionHandling(new ConnectionHandling.BluetoothListener() {
            @Override
            public void onStatusConnection(String status) {
                statusText.append(status);

                Log.d("MainActivity", status);
            }
            @Override
            public void onConnected(BluetoothDevice device) {
                statusText.setText("Connected: " + device.getName());

                Log.d("MainActivity", "Connected: " + device.getName());

            }
            @Override
            public void onDisconnected(Boolean state) {
                statusText.append(" Dstate " + state);
                isDiscconnected = state;

               // Log.d("MainActivity", "Disconnected");

            }
            @Override
            public void onConnectionFailed(String message) {
                statusText.append("Connection failed: " + message);
                Log.d("MainActivity", "Connection failed: " + message);
            }
            @Override
            public void onDataReceived(String data) {
                runOnUiThread(() -> {
                    receiveText.append("Empfangene Daten: " + data + " ");
                    Log.d("MainActivity", "Data received: " + data);
                });
            }
        });

        companionDeviceManager = (CompanionDeviceManager) getSystemService(COMPANION_DEVICE_SERVICE);

        pairingReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();

                if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                    if (device != null) {
                        // Wenn das Gerät bereits gekoppelt ist, könntest du hier eine Aktion einleiten
                        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {

                            connectionHandling.connect(device);

                        }else if (device.getBondState() == BluetoothDevice.BOND_NONE) {

                            Log.d("BondState", "Kopplung fehlgeschlagen.");
                        }
                    }

                }else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        //  Log.d(TAG, "Device disconnected: " + device.getName());

                        // Try to reconnect if not disconnected
                        if (!isDiscconnected) {
                            connectionHandling.reconnect(device);
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        // BroadcastReceiver registrieren
        registerReceiver(pairingReceiver, filter);

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                // Berechtigung erteilt
                initializeBluetoothFeatures();

            } else {
                // Berechtigung verweigert
                Toast.makeText(this, "Bluetooth-Berechtigung erforderlich", Toast.LENGTH_SHORT).show();
            }
        });

        //Prüfe ob Berechtigung vorhanden
        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // Berechtigung anfordern
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_CODE);
        } else {
            // Berechtigung ist bereits erteilt
            initializeBluetoothFeatures();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(pairingReceiver);
            connectionHandling.disconnect();
        } catch (Exception e) {
            Log.e("MainActivity", "Receiver already unregistered", e);
        }
        // Falls du eine Bluetooth-Verbindung hergestellt hast, kannst du diese hier schließen
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            // Wenn Bluetooth aktiviert ist, deaktiviere es
            if (bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.disable();
                Log.d("MainActivity", "Bluetooth deaktiviert.");
            }
        }
    }

/*********************************Bluetooth Features*************************************/
    private void initializeBluetoothFeatures() {
        // Hier deine Bluetooth-Funktionen starten
        Toast.makeText(this, "Bluetooth-Berechtigung erteilt", Toast.LENGTH_SHORT).show();
        SharedPreferences sharedPref = getSharedPreferences("bluetooth_prefs", Context.MODE_PRIVATE);
        String deviceAddress = sharedPref.getString("device_address", null);
        statusText.append(" ini1 " + deviceAddress );
        if (deviceAddress != null) {
            statusText.append(" ini2 " );
            BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
            connectionHandling.connect(device);

        }
    }

    /*********************************Companion Device Manager*************************************/
    private void associateDevice() {

        BluetoothDeviceFilter deviceFilter = new BluetoothDeviceFilter.Builder()
                //   .addDeviceType(CompanionDeviceFilter.DEVICE_TYPE_BLUETOOTH)
                .build(); // no filter

        AssociationRequest pairingRequest = new AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .setSingleDevice(false)
                .build();

        Executor executor = new Executor() {
            @Override
            public void execute(Runnable runnable) {
                runnable.run();
            }
        };

        companionDeviceManager.associate(pairingRequest, executor, new CompanionDeviceManager.Callback() {

            @Override
            public void onDeviceFound(IntentSender chooserLauncher) {
                statusText.append(" ad2" );
                try {
                    startIntentSenderForResult(
                            chooserLauncher, SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0
                    );
                } catch (IntentSender.SendIntentException e) {
                    Log.e("MainActivity", "Failed to send intent");
                }
            }

            @Override
            public void onAssociationCreated(AssociationInfo associationInfo) {
                statusText.append(" ad3" );

                int associationId = associationInfo.getId();
                MacAddress macAddress = associationInfo.getDeviceMacAddress();
                AssociatedDevice associatedDevice = associationInfo.getAssociatedDevice();

                // Ausgabe für Debugging
                Log.d("Association", "Assoziations-ID: " + associationId);
                Log.d("Association", "MAC-Adresse: " + macAddress);

                // BluetoothAdapter über BluetoothManager erhalten

                if (associatedDevice != null) {

                    statusText.append(" ad4 ");

                    // Hier kommt das Bluetooth-Gerät über das AssociatedDevice-Objekt
                    BluetoothDevice device = associatedDevice.getBluetoothDevice();

                    if (device != null) {


                        int bondState = device.getBondState();
                        if(bondState  == BluetoothDevice.BOND_BONDED){

                            saveDeviceAddress(device);// Shared Preferences
                            statusText.append(" Bond " + device.getName());
                            connectionHandling.connect(device);
                        }
                        else if (bondState == BluetoothDevice.BOND_NONE) {
                            statusText.append(" Bond_None pair ");
                            pairDevice(device);// Gerät ist noch nicht gekoppelt, versuche die Kopplung
                        }
                    }
                    else{
                        Log.e("MainActivity", "Device to pair is null");
                        statusText.setText(" Device to pair is null ");
                    }
                }
            }

            @Override
            public void onFailure(@Nullable CharSequence charSequence) {
                statusText.append("Fehler " + charSequence.toString()  );
            }
        });
    }

    private void pairDevice(BluetoothDevice device) {
        try {

            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                Log.d("MainActivity", "Initiating pairing with device: " + device.getName());
                statusText.append(" pairing ");
                device.createBond();
                Log.d("MainActivity", "createBond result: ");
            } else {
                Log.d("MainActivity", "Device already bonded: " + device.getName());
                connectionHandling.connect(device);
            }
        } catch (SecurityException e) {
            Log.e("MainActivity", "Pairing failed", e);
        }
    }


    /**********************************Shared Preferences*************************************/
    private void saveDeviceAddress(BluetoothDevice device) {
        SharedPreferences sharedPref = getSharedPreferences("bluetooth_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("device_address", device.getAddress());
        editor.apply();
        Log.d("Bluetooth", "Geräteadresse gespeichert.");
       // Toast.makeText(this, "Save device for new app start" + device.getAddress(), Toast.LENGTH_SHORT).show();
    }

    private void clearSavedDeviceAddress() {
        SharedPreferences sharedPref = getSharedPreferences("bluetooth_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove("device_address"); // Spezifischer und klarer als `.putString(key, null)`
        editor.apply();
        Log.d("Bluetooth", "Gespeicherte Geräteadresse entfernt.");
       // Toast.makeText(this, "Delete saved device", Toast.LENGTH_SHORT).show();
    }


    /**************************OptionsMenü**************************************/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Menülayout in die Activity einbinden
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Klicks auf Menüelemente behandeln
        int id = item.getItemId();

        if (id == R.id.action_btn_connect_bluetooth) {
            associateDevice();
            return true;
        } else if (id == R.id.action_btn_disconnect_bluetooth) {
            connectionHandling.disconnect();
            clearSavedDeviceAddress();
            return true;
        }
        else if (id == R.id.action_btn_single_socket) {
            Toast.makeText(this, "Other", Toast.LENGTH_SHORT).show();
            return true;
        }
        else if (id == R.id.action_btn_settings) {
            Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}