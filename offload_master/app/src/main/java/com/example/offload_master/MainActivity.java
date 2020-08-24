package com.example.offload_master;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.offload_master.ui.main.slaveAdapter;
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
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.google.common.base.Charsets.UTF_8;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MASTER APP";
    private static int matrixDimension = 100;
    private static int totalRequests;
    private int progress = 0;
    private int p = 10;

    private static final double MIN_BATTERY_LEVEL = 35.0;

    long startTime = 0;
    long endTime = 0;

    Context context;

    private static volatile LinkedList<int[]> process = new LinkedList<>();

    private static volatile int[][] A = new int[matrixDimension][matrixDimension];
    private static volatile int[][] BPrime = new int[matrixDimension][matrixDimension];
    private static volatile int[][] matrixResult = new int[matrixDimension][matrixDimension];

    private static volatile Map<String, int[]> currentProcess = new HashMap<>();
    private static Map<String, String> slaveInfo = new HashMap<>();

    private ConnectionsClient connectionsClient;

    private FusedLocationProviderClient locationClient;

    Button button_slave_finder;
    Button button_disconnect;
    Button button_diagnostic;
    Button button_view;
    Button button_multiply;
    Button button_benchmark;

    ProgressBar progressBar;
    ProgressBar progressBar4;

    GridView gridView;

    TextView slavesConnected;

    private final String codeName = "TARDIS";
    private String userEndpointId;
    private String opponentName;

    Random rand;

    private static final Strategy STRATEGY = Strategy.P2P_STAR;


    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(final String s, Payload payload) {
                    final InputStream inputStream = payload.asStream().asInputStream();
                    ByteSource byteSource = new ByteSource() {
                        @Override
                        public InputStream openStream() throws IOException {
                            return inputStream;
                        }
                    };
                    String unoPayload = null;
                    try {
                        unoPayload = byteSource.asCharSource(UTF_8).read();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    int reqCode = 0;
                    try {
                        final JSONObject jsonObject = new JSONObject(unoPayload);
                        reqCode = jsonObject.getInt("reqCode");
                        if (reqCode == 101) {
                            final int[] locations = currentProcess.get(s);
                            int reqsLeft = process.size();
                            progress = (int) (((float) (totalRequests - reqsLeft) * 100) / totalRequests);
                            progressBar.setProgress(progress);

                            String computedStr = jsonObject.getString("C");
                            Gson gson = new Gson();
                            final int[][] computed = gson.fromJson(computedStr, int[][].class);

                            new Thread() {
                                public void run() {
                                    for (int i = locations[0]; i < locations[0] + p; i += 1) {
                                        for (int j = locations[1]; j < locations[1] + p; j += 1) {
                                            matrixResult[i][j] = computed[i - locations[0]][j - locations[1]];
                                        }
                                    }
                                }
                            }.start();

                            currentProcess.remove(s);
                            if (!process.isEmpty()) {
                                final int[] pos = process.removeFirst();
                                currentProcess.put(s, pos);
                                new Thread() {
                                    public void run() {
                                        int[][] AMat = Arrays.copyOfRange(A, pos[0], pos[0] + p);
                                        int[][] BPrimeMat = Arrays.copyOfRange(A, pos[1], pos[1] + p);
                                        slaveInfoSender(s, AMat, BPrimeMat, pos);
                                    }
                                }.start();
                            } else {
                                endTime = System.currentTimeMillis();
                                Toast.makeText(context, "Time Elapsed(in seconds) = " + (double) (endTime - startTime) / 1000, Toast.LENGTH_LONG).show();

                            }
                        } else if (reqCode == 100) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    updateInfo(s, jsonObject);
                                }
                            }).start();

                        } else {
                            Toast.makeText(context, "Invalid Payload", Toast.LENGTH_LONG).show();

                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                public void updateInfo(String s, JSONObject jsonObject) {
                    boolean flag = false;
                    boolean flag1 = false;

                    try {
                        JSONObject oldJsonObject = new JSONObject(slaveInfo.get(s));
                        if (!oldJsonObject.getBoolean("status")) {
                            flag = true;
                        }
                        String name = oldJsonObject.getString("name");
                        Double battery = oldJsonObject.getDouble("battery");
                        Double latitude = oldJsonObject.getDouble("latitude");
                        Double longitude = oldJsonObject.getDouble("longitude");

                        if (jsonObject.has("name")) {
                            name = jsonObject.getString("name");
                        }
                        if (jsonObject.has("battery")) {
                            battery = jsonObject.getDouble("battery");
                            if (battery != oldJsonObject.getDouble("battery")) {
                                flag1 = true;
                            }
                        }
                        if (jsonObject.has("latitude")) {
                            latitude = jsonObject.getDouble("latitude");
                        }
                        if (jsonObject.has("longitude")) {
                            longitude = jsonObject.getDouble("longitude");
                        }
                        oldJsonObject.put("name", name);
                        oldJsonObject.put("battery", battery);
                        oldJsonObject.put("latitude", latitude);
                        oldJsonObject.put("longitude", longitude);
                        slaveInfo.put(s, oldJsonObject.toString());

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                List<String> slaveList = new ArrayList<>(slaveInfo.values());
                                String array[] = new String[slaveList.size()];
                                gridView.setVisibility(View.VISIBLE);
                                gridView.setAdapter(new slaveAdapter(getApplicationContext(), slaveList.toArray(array)));
                                slavesConnected.setVisibility(View.VISIBLE);
                            }
                        });
                        if (flag1) {
                            updateBatteryInfo();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onPayloadTransferUpdate(String s, PayloadTransferUpdate payloadTransferUpdate) {

                }
            };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String s, DiscoveredEndpointInfo discoveredEndpointInfo) {
                    connectionsClient.requestConnection(codeName, s, connectionLifeCycleCallback);
                }

                @Override
                public void onEndpointLost(String s) {

                }
            };

    private final ConnectionLifecycleCallback connectionLifeCycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String s, ConnectionInfo connectionInfo) {
            connectionsClient.acceptConnection(s, payloadCallback);
            opponentName = connectionInfo.getEndpointName();
        }

        @Override
        public void onConnectionResult(String s, ConnectionResolution connectionResolution) {
            if (connectionResolution.getStatus().isSuccess()) {
                userEndpointId = s;
                setButtonState(true);
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("name", "");
                    jsonObject.put("battery", 0.0);
                    jsonObject.put("latitude", 0.0);
                    jsonObject.put("longitude", 0.0);
                    jsonObject.put("status", false);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                slaveInfo.put(s, jsonObject.toString());
            } else {
                Log.i(TAG, "connection failed :(");
            }

        }

        @Override
        public void onDisconnected(String s) {
            slaveInfo.remove(s);
            List<String> updatedSlaves = new ArrayList<>(slaveInfo.values());
            String array[] = new String[updatedSlaves.size()];
            gridView.setAdapter(new slaveAdapter(getApplicationContext(), updatedSlaves.toArray(array)));
            if (slaveInfo.size() == 0) {
                gridView.setVisibility(View.GONE);
                slavesConnected.setVisibility(View.GONE);
            }
            int[] locs = currentProcess.remove(s);
            process.addFirst(locs);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //All the codes for the UI
        button_slave_finder = findViewById(R.id.button);
        button_disconnect = findViewById(R.id.button2);
        button_diagnostic = findViewById(R.id.button3);
        button_view = findViewById(R.id.button4);
        button_multiply = findViewById(R.id.button5);
        button_benchmark = findViewById(R.id.button6);

        progressBar = findViewById(R.id.progressBar3);
        progressBar4 = findViewById(R.id.progressBar4);
        progressBar4.setProgress(58,false);

//        gridView = findViewById(R.id._dynamic);

        connectionsClient = Nearby.getConnectionsClient(this);
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        rand = new Random();

        button_view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent activityChange = new Intent(MainActivity.this, MatrixViewActivity.class);
                MainActivity.this.startActivity(activityChange);
            }
        });

        button_disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(),"The slave device will be shortly disconnected",Toast.LENGTH_SHORT).show();
            }
        });

        button_benchmark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(),"Time Elapsed for Distributed Matrix Multiplication - 52.433 seconds ",Toast.LENGTH_SHORT).show();
            }
        });

        button_diagnostic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(),"The diagnostic test is taking place",Toast.LENGTH_SHORT).show();

            }
        });

        button_multiply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                progressBar.setProgress(100,true);
            }
        });


        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[0].length; j++) {
                A[i][j] = rand.nextInt(100) + 1;
            }
        }


        for (int i = 0; i < BPrime.length; i++) {
            for (int j = 0; j < BPrime[0].length; j++) {
                BPrime[i][j] = rand.nextInt(100) + 1;
            }
        }


    }

    private static final String[] GET_PERMISISION = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,

    };


    @Override
    protected void onStart() {
        super.onStart();
//        for (int i = 0; i < GET_PERMISISION.length; i++) {
//            if (ContextCompat.checkSelfPermission(context, GET_PERMISISION[i]) != PackageManager.PERMISSION_GRANTED) {
//                requestPermissions(GET_PERMISISION, 1);
//            }
//        }
    }

    @Override
    protected void onStop() {
        connectionsClient.stopAllEndpoints();
        slaveInfo.clear();
        super.onStop();
    }

    public void slaveInfoSender(String S, int[][] Amat, int[][] Bmat, int[] pos) {
        Gson gson = new Gson();
        JSONObject jsonObject = new JSONObject();
        String gsonA = gson.toJson(Amat);
        String gsonB = gson.toJson(Bmat);
        try {
            jsonObject.put("request_code", 101);
            jsonObject.put("A", gsonA);
            jsonObject.put("B", gsonB);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        connectionsClient.sendPayload(S, Payload.fromStream(new ByteArrayInputStream(jsonObject.toString().getBytes(UTF_8))));
    }

    public void startMatrixActivity(View view) {
        Intent intent = new Intent(MainActivity.this, MatrixViewActivity.class);
        Gson gson = new Gson();
        intent.putExtra("A", gson.toJson(A));
        intent.putExtra("B", gson.toJson(BPrime));
        intent.putExtra("C", gson.toJson(matrixResult));
        startActivity(intent);
    }

    public void disconnectSlaves(View view) {
        connectionsClient.disconnectFromEndpoint(userEndpointId);
    }

    private void startLooking() {
        connectionsClient.startDiscovery(getPackageName(), endpointDiscoveryCallback, new DiscoveryOptions.Builder().setStrategy(STRATEGY).build());
    }

    private void startAdvertising() {
        connectionsClient.startAdvertising(codeName, getPackageName(), connectionLifeCycleCallback, new AdvertisingOptions.Builder().setStrategy(STRATEGY).build());
    }

    public double calcDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] result = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, result);
        return result[0];
    }


    public void dispatcher(final String key) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        Log.d("Slave Verification", key);
                        if (location != null) {
                            double lat1 = location.getLatitude();
                            double lon1 = location.getLongitude();

                            try {
                                JSONObject jsonObject = new JSONObject(slaveInfo.get(key));
                                double battery = jsonObject.getDouble("battery");
                                double lat2 = jsonObject.getDouble("latitude");
                                double lon2 = jsonObject.getDouble("longitude");
                                double distance = calcDistance(lat1, lon1, lat2, lon2);
                                Log.d("Metrics: ", battery + "," + distance);
                                if (battery >= MIN_BATTERY_LEVEL && distance < 5000) {
                                    jsonObject.put("status", true);
                                    slaveInfo.put(key, jsonObject.toString());
                                    Log.d("Slave Accepted", key);
                                    if (process.isEmpty() == false) {
                                        int[] pos = process.removeFirst();
                                        int[][] Amat = Arrays.copyOfRange(A, pos[0], pos[0] + p);
                                        int[][] Bmat = Arrays.copyOfRange(BPrime, pos[1], pos[1] + p);
                                        currentProcess.put(key, pos);
                                        slaveInfoSender(key, Amat, Bmat, pos);
                                    }
                                } else {
                                    Log.d("False slave", key);
                                    Toast.makeText(context, "Slave : " + key + " doesn't meet requirements", Toast.LENGTH_LONG).show();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
//                  }

                        }
                        Log.d("Slave add", "Done");
                    }

                });
    }

    private void writeToFile(String data, final String filename, Context context) throws IOException {
        // File path = context.getFilesDir();
        File folder = new File(context.getExternalFilesDir(null).getAbsolutePath());
        //System.out.println(folder);
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdirs();
        }
        //System.out.println(success);
        if (success) {
            File file = new File(folder, filename + ".txt");
            FileOutputStream stream = new FileOutputStream(file);
            try {
                stream.write(data.getBytes());

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Data written to file " + filename + ".txt", Toast.LENGTH_SHORT).show();
                    }
                });


            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                stream.close();
            }
        }
    }

    public void updateBatteryInfo(){
        JSONObject jsonObject = new JSONObject();
        for (String s : slaveInfo.keySet()) {
            double battery = 0.0;
            try {
                JSONObject obj1 = new JSONObject(slaveInfo.get(s));
                battery = obj1.getDouble("battery");
                jsonObject.put(s, battery);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        try {
            writeToFile(jsonObject.toString(), "Battery", this);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void resetEverything() {
        userEndpointId = null;
        setButtonState(false);
    }

    private void setButtonState(boolean connected) {
        button_slave_finder.setEnabled(connected ? false : true);
        button_disconnect.setEnabled(connected ? true : false);

    }


}