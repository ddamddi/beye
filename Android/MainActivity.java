package com.example.parkjinhyuk.beye;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;


public class MainActivity extends AppCompatActivity {

    private Button btnShowLocation;
    private TextView txtLat;
    private TextView txtLon;
    private final int PERMISSIONS_ACCESS_FINE_LOCATION = 1000;
    private final int PERMISSIONS_ACCESS_COARSE_LOCATION = 1001;
    private boolean isAccessFineLocation = false;
    private boolean isAccessCoarseLocation = false;
    private boolean isPermission = false;

    private GpsInfo gps;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnShowLocation = (Button) findViewById(R.id.btn_start);
        txtLat = (TextView) findViewById(R.id.tv_latitude);
        txtLon = (TextView) findViewById(R.id.tv_longitude);

        // GPS 정보를 보여주기 위한 이벤트 클래스 등록
        btnShowLocation.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                // 권한 요청을 해야 함
                if (!isPermission) {
                    callPermission();
                    return;
                }

                gps = new GpsInfo(MainActivity.this);
                // GPS 사용유무 가져오기
                if (gps.isGetLocation()) {

                    double latitude = gps.getLatitude();
                    double longitude = gps.getLongitude();

//                    findRoute(37.56461982743129, 126.9823439963945);
                    findRoute(latitude, longitude);
//                    tmapApi.setLatitude(latitude);
//                    tmapApi.setLongitude(longitude);
//                    tmapApi.findRoute();

                    txtLat.setText(String.valueOf(latitude));
                    txtLon.setText(String.valueOf(longitude));

                    Toast.makeText(
                            getApplicationContext(),
                            "당신의 위치 - \n위도: " + latitude + "\n경도: " + longitude,
                            Toast.LENGTH_LONG).show();


                } else {
                    // GPS 를 사용할수 없으므로
                    gps.showSettingsAlert();
                }
            }
        });

        callPermission();  // 권한 요청을 해야 함
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == PERMISSIONS_ACCESS_FINE_LOCATION
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            isAccessFineLocation = true;

        } else if (requestCode == PERMISSIONS_ACCESS_COARSE_LOCATION
                && grantResults[0] == PackageManager.PERMISSION_GRANTED){

            isAccessCoarseLocation = true;
        }

        if (isAccessFineLocation && isAccessCoarseLocation) {
            isPermission = true;
        }
    }

    // 전화번호 권한 요청
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
                != PackageManager.PERMISSION_GRANTED){

            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSIONS_ACCESS_COARSE_LOCATION);
        } else {
            isPermission = true;
        }
    }

    private void findRoute(final double latitude, final double longitude) {

        System.out.println("위도 : " + latitude);
        System.out.println("경도 : " + longitude);

        Thread thread = new Thread() {
            @Override
            public void run() {
                HttpURLConnection conn = null;

                try {
                    URL url = new URL("https://api2.sktelecom.com/tmap/routes/pedestrian?version=1" +
                            "&format=json" +
                            "&appKey=46517717-fc78-4a30-b3ec-72635f6a8119" +
                            "&startX=" +
                            longitude +
                            "&startY=" +
                            latitude +
                            "&angle=1" +
                            "&speed=3" +
                            "&endPoiId=334852" +
                            "&endRpFlag=8" +
                            "&endX=126.958102" +
                            "&endY=37.505020" +
//                            "&passList=126.98506595175428,37.56674182109044,334857,16" +
                            "&reqCoordType=WGS84GEO" +
                            "&gpsTime=15000" +
                            "&startName=%EC%B6%9C%EB%B0%9C" +
                            "&endName=%EB%B3%B8%EC%82%AC" +
                            "&searchOption=0" +
                            "&resCoordType=WGS84GEO");



                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(60);

//                    OutputStream os = conn.getOutputStream();
//                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
//                    bw.write("&startX=126.9823439963945"
//                            + "&startY=37.56461982743129"
//                            + "&angle=1"
//                            + "&speed=60"
//                            + "&endPoiId=334852"
//                            + "&endRpFlag=8"
//                            + "&endX=126.98031634883303"
//                            + "&endY=37.57007473965354"
//                            + "&passList=126.98506595175428,37.56674182109044,334857,16"
//                            + "&reqCoordType=WGS84GEO"
//                            + "&gpsTime=15000"
//                            + "&startName=출발"
//                            + "&endName=본사"
//                            + "&searchOption=0"
//                            + "&resCoordType=WGS84GEO"
//                    );

                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(line);
                    }

                    JSONObject jAr = new JSONObject(sb.toString());
                    JSONArray features = jAr.getJSONArray("features");
                    for (int i = 0; i < features.length(); i++) {
                        JSONObject test = features.getJSONObject(i);
                        JSONObject properties = test.getJSONObject("properties");
                        System.out.println(properties.getString("description"));
                    }


                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }
}