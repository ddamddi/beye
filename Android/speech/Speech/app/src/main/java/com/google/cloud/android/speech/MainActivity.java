/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.android.speech;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class MainActivity extends AppCompatActivity implements MessageDialogFragment.Listener, OnMapReadyCallback {



    int mPairedDeviceCount;
    Set<BluetoothDevice> mDevices;






    private GpsInfo gps;
    private GoogleMap mMap;
    private Geocoder geocoder;
    private String destination;

    private final int PERMISSIONS_ACCESS_FINE_LOCATION = 1000;
    private final int PERMISSIONS_ACCESS_COARSE_LOCATION = 1001;
    private final int REQUEST_ENABLE_BT = 1;
    private static final String FRAGMENT_MESSAGE_DIALOG = "message_dialog";
    private static final String STATE_RESULTS = "results";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;
    private boolean isAccessFineLocation = false;
    private boolean isAccessCoarseLocation = false;
    private boolean isPermission = false;

    private SpeechService mSpeechService;
    private VoiceRecorder mVoiceRecorder;

    private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

        @Override
        public void onVoiceStart() {
            showStatus(true);
            if (mSpeechService != null) {
                mSpeechService.startRecognizing(mVoiceRecorder.getSampleRate());
            }
        }

        @Override
        public void onVoice(byte[] data, int size) {
            if (mSpeechService != null) {
                mSpeechService.recognize(data, size);
            }
        }

        @Override
        public void onVoiceEnd() {
            showStatus(false);
            if (mSpeechService != null) {
                mSpeechService.finishRecognizing();
            }
        }

    };
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            mSpeechService = SpeechService.from(binder);
            mSpeechService.addListener(mSpeechServiceListener);
            mStatus.setVisibility(View.VISIBLE);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSpeechService = null;
        }

    };

    // Resource caches
    private int mColorHearing;
    private int mColorNotHearing;

    // View references
    private Button btnShowLocation;
    private ToggleButton btnVoice;
    private TextView mStatus;
    private TextView mText;
    private ResultAdapter mAdapter;
    private RecyclerView mRecyclerView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            //장치가 블루투스를 지원하지 않는 경우.
            finish();   // 어플리케이션 종료
        } else {
            // 장치가 블루투스를 지원하는 경우.
            if (!mBluetoothAdapter.isEnabled()) {
                // 블루투스를 지원하지만 비활성 상태인 경우
                // 블루투스를 활성 상태로 바꾸기 위해 사용자 동의 요첨
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                // 블루투스를 지원하며 활성 상태인 경우
                // 페어링된 기기 목록을 보여주고 연결할 장치를 선택.
                mDevices = mBluetoothAdapter.getBondedDevices();
                mPairedDeviceCount = mDevices.size();

                if (mPairedDeviceCount == 0) {
                    //  페어링 된 장치가 없는 경우
                    finish();    // 어플리케이션 종료
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("블루투스 장치 선택");


                // 페어링 된 블루투스 장치의 이름 목록 작성
                List<String> listItems = new ArrayList<String>();
                for (BluetoothDevice device : mDevices) {
                    listItems.add(device.toString());
                }
                listItems.add("취소");    // 취소 항목 추가

                final CharSequence[] items = listItems.toArray(new CharSequence[listItems.size()]);

//                    builder.setItems(new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int item) {
//                            if (item == mPairedDeviceCount) {
//                                // 연결할 장치를 선택하지 않고 '취소'를 누른 경우
//                                finish();
//                            } else {
//                                // 연결할 장치를 선택한 경우
//                                // 선택한 장치와 연결을 시도함
//                                connectToSelectedDevices(items[item].toString());
//                            }
//                        }
//                    });


//                builder.setCancelable(false);    // 뒤로 가기 버튼 사용 금지
                AlertDialog alert = builder.create();
                alert.show();


            }
        }




        final Resources resources = getResources();
        final Resources.Theme theme = getTheme();
        mColorHearing = ResourcesCompat.getColor(resources, R.color.status_hearing, theme);
        mColorNotHearing = ResourcesCompat.getColor(resources, R.color.status_not_hearing, theme);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        mStatus = (TextView) findViewById(R.id.status);
        mText = (TextView) findViewById(R.id.text);
        btnShowLocation = (Button) findViewById(R.id.btn_start);
        btnVoice = (ToggleButton) findViewById(R.id.toggleButton);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        final ArrayList<String> results = savedInstanceState == null ? null :
                savedInstanceState.getStringArrayList(STATE_RESULTS);
        mAdapter = new ResultAdapter(results);
        mRecyclerView.setAdapter(mAdapter);



        btnShowLocation.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {

                double startLatitude;
                double startLongitude;
                double desLatitude;
                double desLongitude;

                Thread findRouteThread = new FindRouter(getApplicationContext());

                List<Address> addressList = null;

                try {
                    addressList = geocoder.getFromLocationName(destination, 10);
                } catch (IOException e) {

                }
                String[] splitStr = addressList.get(0).toString().split(",");
                String address = splitStr[0].substring(splitStr[0].indexOf("\"") + 1, splitStr[0].length() - 2);
                desLatitude = Double.valueOf(splitStr[10].substring(splitStr[10].indexOf("=") + 1));
                desLongitude = Double.valueOf(splitStr[12].substring(splitStr[12].indexOf("=") + 1));

                ((FindRouter)findRouteThread).setDesLatitude(desLatitude);
                ((FindRouter)findRouteThread).setDesLongitude(desLongitude);

                if (!isPermission) {
                    callPermission();
                    return;
                }

                gps = new GpsInfo(MainActivity.this);
                if (gps.isGetLocation()) {

                    startLatitude = gps.getLatitude();
                    startLongitude = gps.getLongitude();
                    ((FindRouter)findRouteThread).setStartLatitude(startLatitude);
                    ((FindRouter)findRouteThread).setStartLongitude(startLongitude);

                    findRouteThread.start();


                } else {
                    gps.showSettingsAlert();
                }
            }
        });

        callPermission();
    }


    @Override
    protected void onStart() {
        super.onStart();

        // Prepare Cloud Speech API
        bindService(new Intent(this, SpeechService.class), mServiceConnection, BIND_AUTO_CREATE);

        // Start listening to voices
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            btnVoice.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if(isChecked == true) {
                        startVoiceRecorder();
                    }
                    else {
                        stopVoiceRecorder();
                    }
                }
            });
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            showPermissionMessageDialog();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    @Override
    protected void onStop() {
        // Stop listening to voice
        stopVoiceRecorder();

        // Stop Cloud Speech API
        mSpeechService.removeListener(mSpeechServiceListener);
        unbindService(mServiceConnection);
        mSpeechService = null;

        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAdapter != null) {
            outState.putStringArrayList(STATE_RESULTS, mAdapter.getResults());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private void startVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
        }
        mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
        mVoiceRecorder.start();
    }

    private void stopVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
    }

    private void showPermissionMessageDialog() {
        MessageDialogFragment
                .newInstance(getString(R.string.permission_message))
                .show(getSupportFragmentManager(), FRAGMENT_MESSAGE_DIALOG);
    }

    private void showStatus(final boolean hearingVoice) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatus.setTextColor(hearingVoice ? mColorHearing : mColorNotHearing);
            }
        });
    }

    @Override
    public void onMessageDialogDismissed() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO_PERMISSION);
    }

    private final SpeechService.Listener mSpeechServiceListener =
            new SpeechService.Listener() {
                @Override
                public void onSpeechRecognized(final String text, final boolean isFinal) {
                    if (isFinal) {
                        mVoiceRecorder.dismiss();
                    }
                    if (mText != null && !TextUtils.isEmpty(text)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isFinal) {
                                    mText.setText(null);
                                    mAdapter.addResult(text);
                                    mRecyclerView.smoothScrollToPosition(0);
                                } else {
                                    mText.setText(text);
                                    destination = text;
                                }
                            }
                        });
                    }
                }
            };

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
        geocoder = new Geocoder(this);
    }


    private static class ViewHolder extends RecyclerView.ViewHolder {

        TextView text;

        ViewHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.item_result, parent, false));
            text = (TextView) itemView.findViewById(R.id.text);
        }

    }

    private static class ResultAdapter extends RecyclerView.Adapter<ViewHolder> {

        private final ArrayList<String> mResults = new ArrayList<>();

        ResultAdapter(ArrayList<String> results) {
            if (results != null) {
                mResults.addAll(results);
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()), parent);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.text.setText(mResults.get(position));
        }

        @Override
        public int getItemCount() {
            return mResults.size();
        }

        void addResult(String result) {
            mResults.add(0, result);
            notifyItemInserted(0);
        }

        public ArrayList<String> getResults() {
            return mResults;
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_ACCESS_FINE_LOCATION
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            isAccessFineLocation = true;

        } else if (requestCode == PERMISSIONS_ACCESS_COARSE_LOCATION
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            isAccessCoarseLocation = true;
        }

        if (isAccessFineLocation && isAccessCoarseLocation) {
            isPermission = true;
        }

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (permissions.length == 1 && grantResults.length == 1
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                isAccessFineLocation = true;
                startVoiceRecorder();
            } else {
                showPermissionMessageDialog();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    private void callPermission() {
        // Check the SDK version and whether the permission is already granted or not.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_ACCESS_FINE_LOCATION);

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSIONS_ACCESS_COARSE_LOCATION);
        } else {
            isPermission = true;
        }
    }
}
