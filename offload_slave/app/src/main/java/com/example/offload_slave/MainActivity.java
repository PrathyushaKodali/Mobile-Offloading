package com.example.offload_slave;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MainActivity extends AppCompatActivity {


    private static final String TAG = "Offload Slave";

    private static final String[] REQUIRED_PERMISSIONS =
            new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            };

    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private String masterEndpointId;
    private Button findMasterButton;

    private Button disconnectButton;
    private ProgressBar progressBar;
    private TextView progressMessage;

    private Intent batteryStatus;
    private ProgressBar batteryProgress;
    private TextView batteryLevel;
    private TextView latitude;
    private TextView longitude;
    private String masterName;
    private TextView masterText;
    private TextView status;
    private Timer timer;
    private int requestCode;

    private static int requestCounter = 0;

    private ConnectionsClient connClient;

    private FusedLocationProviderClient fusedLocationProviderClient;

    @Override
    protected void onCreate(@Nullable Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        findMasterButton = findViewById(R.id.find_master);
        disconnectButton = findViewById(R.id.disconnect);
        progressBar = (ProgressBar) findViewById(R.id.progressBar3);
        progressBar.setVisibility(View.GONE);
        progressMessage = (TextView) findViewById(R.id.progressMessage);
        progressMessage.setVisibility(View.GONE);
        batteryProgress = (ProgressBar) findViewById(R.id.progressBar6);
        batteryLevel = (TextView) findViewById(R.id.batteryLevel);
        latitude = (TextView) findViewById(R.id.latitude);
        longitude = (TextView) findViewById(R.id.longitude);
        latitude.setVisibility(View.GONE);
        longitude.setVisibility(View.GONE);
        connClient = Nearby.getConnectionsClient(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        setUiLocation();
        masterText = findViewById(R.id.master_name);
        status = findViewById(R.id.status);
        TextView nameView = findViewById(R.id.name);
        nameView.setText(getString(R.string.slaveName, "Slave device"));
        TimerTask timerTask = new updateBatteryLevel();
        timer = new Timer(true);
        timer.scheduleAtFixedRate(timerTask, 0, 10 * 1000);
        startDiscovery();
        resetUI();
        findMasterButton.setVisibility(View.GONE);
    }

    private static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    @Override
    protected void onStart() {
        super.onStart();

        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            requestPermissions(REQUIRED_PERMISSIONS, 1);
        }
    }

    @Override
    protected void onStop() {
        connClient.stopAllEndpoints();
        super.onStop();
    }

    public class BatteryLocationTimer extends TimerTask {
        @Override
        public void run() {
            sendBatteryLocationData(100);
        }

    }

    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    try {
                        final InputStream is = payload.asStream().asInputStream();
                        ByteSource byteSource = new ByteSource() {
                            @Override
                            public InputStream openStream() throws IOException {
                                return is;
                            }
                        };

                        JSONObject jsonObject = null;

                        try {
                            String data = byteSource.asCharSource(Charsets.UTF_8).readFirstLine();
                            jsonObject = new JSONObject(data);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (jsonObject.has("request_code")) {
                            requestCode = jsonObject.getInt("request_code");
                        }
                        if (requestCode == 100) {
                            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                            alertDialogBuilder.setMessage("Request received for monitoring. Accept?");
                            alertDialogBuilder.setPositiveButton("yes",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface arg0, int arg1) {
                                            TimerTask timerTask = new BatteryLocationTimer();
                                            timer = new Timer(true);
                                            timer.scheduleAtFixedRate(timerTask, 0, 20 * 1000);
                                        }
                                    });

                            alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    JSONObject obj = new JSONObject();
                                    try {
                                        obj.put("requestCode", requestCode);
                                        obj.put("requestStatus", 500);
                                        obj.put("deviceName", "slave device");
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }

                                    Log.println(Log.INFO, "Request  : ", obj.toString());
                                    connClient.sendPayload(
                                            masterEndpointId, Payload.fromStream(new ByteArrayInputStream(obj.toString().getBytes(UTF_8))));
                                    finish();
                                }
                            });

                            AlertDialog alertDialog = alertDialogBuilder.create();
                            alertDialog.show();

                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                }
            };

    @CallSuper
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode != 1) {
            return;
        }

        for (int grantResult : results) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "App doesn't have permissions", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
        recreate();
    }

    private void resetUI() {
        masterEndpointId = null;
        masterName = null;
        setStatus(getString(R.string.STATUS_DISCONNECTED));
        setMasterName(getString(R.string.no_master));
        setButtonState(false);
    }


    private void setMasterName(String masterName) {
        masterText.setText(getString(R.string.master_name, masterName));
    }

    void setUiLocation() {
        fusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            latitude.setText("Latitude : " + location.getLatitude());
                            longitude.setText("Longitude : " + location.getLongitude());
                            longitude.setVisibility(View.VISIBLE);
                            latitude.setVisibility(View.VISIBLE);

                        }
                    }

                }).addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e("failure", e.getMessage());
            }
        }).addOnCanceledListener(this, new OnCanceledListener() {
            @Override
            public void onCanceled() {
                Log.d("Cancelled", "Cancelled");
            }
        }).addOnCompleteListener(this, new OnCompleteListener<Location>() {
            @Override
            public void onComplete(@NonNull Task<Location> task) {

                Log.d("Completed", "completed");
            }
        });

    }

    public class updateBatteryLevel extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    double batteryStatus = getBatteryLevel();
                    batteryProgress = (ProgressBar) findViewById(R.id.progressBar6);
                    batteryProgress.setProgress((int) batteryStatus);
                    batteryLevel.setText(73.0 + "%");

                }
            });
        }
    }

    private double getBatteryLevel() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = this.registerReceiver(null, intentFilter);
        int bLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 0);

        return bLevel * 100 / (double) scale;
    }

    private void startDiscovery() {
        connClient.startDiscovery(
                getPackageName(), endpointDiscoveryCallback,
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build());
    }


    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    Log.i(TAG, "Endpoint found: connecting");
                    connClient.requestConnection("slave", endpointId, connectionLifecycleCallback);
                }

                @Override
                public void onEndpointLost(String endpointId) {
                }
            };

    private void startAdvertising() {
        connClient.startAdvertising(
                "slave", getPackageName(), connectionLifecycleCallback,
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).build());
    }


    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(final String endpointId, final ConnectionInfo connInfo) {


                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                    alertDialogBuilder.setMessage("Accept request?");
                    alertDialogBuilder.setPositiveButton("yes",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int arg1) {
                                    Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                                    connClient.acceptConnection(endpointId, payloadCallback);
                                    masterName = connInfo.getEndpointName();
                                }
                            });

                    alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int arg0) {
                            finish();
                        }
                    });

                    AlertDialog alertDialog = alertDialogBuilder.create();
                    alertDialog.show();


                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        Log.i(TAG, "connection successful");
                        sendBatteryLocationData(100);
                        masterEndpointId = endpointId;
                        setMasterName(masterName);
                        setStatus(getString(R.string.STATUS_CONNECTED));
                        setButtonState(true);

                    } else {
                        Log.e(TAG, "connection failed");
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    Log.i(TAG, "Disconnected from Master");
                    findMasterButton.setVisibility(View.VISIBLE);
                    progressMessage.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    connClient.stopDiscovery();
                    requestCounter = 0;
                    resetUI();
                    if (timer != null)
                        timer.cancel();
                }
            };

    private void setButtonState(boolean connected) {
        findMasterButton.setEnabled(true);
        findMasterButton.setVisibility(connected ? View.GONE : View.VISIBLE);
        disconnectButton.setVisibility(connected ? View.VISIBLE : View.GONE);
    }

    public void disconnect(View view) {
        connClient.disconnectFromEndpoint(masterEndpointId);
        resetUI();
    }

    public void findMaster(View view) {
        startDiscovery();
        setStatus(getString(R.string.status_searching));
        findMasterButton.setEnabled(false);
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    private void sendBatteryLocationData(final int code) {
        fusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location loc) {
                        if (loc != null) {
                            JSONObject json = new JSONObject();
                            try {
                                json.put("request_code", code);
                                json.put("request_status", 200);
                                json.put("name", "slave device");
                                json.put("batteryLevel", getBatteryLevel());
                                json.put("latitude", loc.getLatitude());
                                json.put("longitude", loc.getLongitude());
                            } catch (JSONException e) {
                                Log.e(TAG, "error: Failed to parse json object");
                            }

                            Log.println(Log.INFO, "json string : ", json.toString());
                            connClient.sendPayload(
                                    masterEndpointId, Payload.fromStream(new ByteArrayInputStream(json.toString().getBytes(UTF_8))));
                        }
                    }
                });
    }

}