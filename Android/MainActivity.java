package com.example.parkjinhyuk.beye;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getInformationFromWeb();
    }


    private void getInformationFromWeb() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                HttpURLConnection conn = null;

                try {
                    URL url = new URL("https://api2.sktelecom.com/tmap/routes/pedestrian?version=1" +
                            "&format=json" +
                            "&appKey=46517717-fc78-4a30-b3ec-72635f6a8119" +
                            "&startX=126.9823439963945" +
                            "&startY=37.56461982743129" +
                            "&angle=1&speed=60" +
                            "&endPoiId=334852" +
                            "&endRpFlag=8" +
                            "&endX=126.98031634883303" +
                            "&endY=37.57007473965354" +
                            "&passList=126.98506595175428,37.56674182109044,334857,16" +
                            "&reqCoordType=WGS84GEO" +
                            "&gpsTime=15000" +
                            "&startName=%EC%B6%9C%EB%B0%9C" +
                            "&endName=%EB%B3%B8%EC%82%AC" +
                            "&searchOption=0" +
                            "&resCoordType=WGS84GEO" );

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