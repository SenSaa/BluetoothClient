package com.example.yusuf.bluetoothclient;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends WearableActivity {

    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);
    private TextView mClockView;

    BluetoothAdapter bluetoothAdapter;
    String serverMsg = "";
    RadioGroup radioGroup;
    TextView serverMsgTxV;
    Button voiceIpBtn;
    Button btConnectOffBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setAmbientEnabled();

        mClockView = (TextView) findViewById(R.id.clock);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        Button btBtn = (Button) findViewById(R.id.btBtn);
        btBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pairedDevices();
            }
        });

        voiceIpBtn = (Button) findViewById(R.id.voiceIpBtn);
        voiceIpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                displaySpeechRecognizer();
            }
        });

        radioGroup = (RadioGroup) findViewById(R.id.radioGroup);
        serverMsgTxV = (TextView) findViewById(R.id.serverMsgTxV);

    }


    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }
    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        updateDisplay();
    }
    @Override
    public void onExitAmbient() {
        updateDisplay();
        super.onExitAmbient();
    }

    private void updateDisplay() {
        if (isAmbient()) {
            //mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
            //mTextView.setTextColor(getResources().getColor(android.R.color.white));
            mClockView.setVisibility(View.VISIBLE);

            mClockView.setText(AMBIENT_DATE_FORMAT.format(new Date()));
        } else {
            //mContainerView.setBackground(null);
            //mTextView.setTextColor(getResources().getColor(android.R.color.black));
            mClockView.setVisibility(View.GONE);
        }
    }


    /* ___ Finding paired devices ___ */
    private void pairedDevices() {
        // - Querying paired devices -
        final Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                Log.v("Paired Devices", device.getName() + "\n");
                Log.v("Paired Devices",device.getAddress() + "\n");

                final ConnectThread connectThread = new ConnectThread(device);

                if (device.getAddress().equals("C4:43:8F:A1:D8:2A")) {
                    connectThread.start();
                }

                btConnectOffBtn = (Button) findViewById(R.id.btConnectOffBtn);
                btConnectOffBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        connectThread.close();
                    }
                });
            }
        }
    }


    /* ___ Connecting to a Server. ___*/
    private class ConnectThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        ConnectedThread connectedThread;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                UUID MY_UUID = UUID.fromString("0ae3d869-470e-45d1-b147-a10882fc1bd2");
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);

                Log.d("Connecting","To BT Device");

            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
                Log.d("Connected", "To Server");
            }
            catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                cancel();
            }

            // Do work to manage the connection (in a separate thread).
            connectedThread = new ConnectedThread(mmSocket);
            connectedThread.start();

            radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    if (checkedId == R.id.sendRadBtn) {
                        Log.d("RadioButton", "Receive");
                        connectedThread.write();
                    } else if (checkedId == R.id.receiveRadBtn) {
                        Log.d("RadioButton", "Send");
                        connectedThread.read();
                        serverMsgTxV.setText(serverMsg);
                        Log.i("Server MSG", serverMsg);
                    }
                }
            });
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }

        private void close() {
            connectedThread.closeConnection();
        }
    }


    /* Managing Connection */

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        PrintWriter outputStream;
        BufferedReader inputStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();

                outputStream = new PrintWriter(socket.getOutputStream(), true);
                Log.d("Output Stream","Initialised");
                inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
        }

        private void write() {
            // To ensure that the spoken text is added to the output stream - add a conditional statement to check if spoken text variable is not null.
            if (spokenText != null) {
                outputStream.println(spokenText);
                Log.d("Output Stream", spokenText);
                Log.d("Output Stream", "Written to Server");
            }
        }

        private void read() {
            try {
                serverMsg = inputStream.readLine();
                Log.i("Server MSG", serverMsg);
                Log.v("InputStream", "Read");
            }
            catch(IOException e){
                e.printStackTrace();
            }
        }

        /* Shutdown the connection */
        public void closeConnection() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }

    }


    // ___ Voice Input ___ //
    private static final int SPEECH_REQUEST_CODE = 0;
    String spokenText;

    // Create an intent that can start the Speech Recognizer activity
    private void displaySpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // Start the activity, the intent will be populated with the speech text
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    // This callback is invoked when the Speech Recognizer returns.
    // This is where you process the intent and extract the speech text from the intent.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            spokenText = results.get(0); // The resulting speech text.
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


}
