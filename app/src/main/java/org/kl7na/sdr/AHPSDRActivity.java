package org.kl7na.sdr;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.media.AudioManager;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import org.apache.http.util.ByteArrayBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import android.util.DisplayMetrics;
import android.widget.Toast;

//import org.bitcoin.protocols.payments.Protos;
//import com.google.bitcoin.core.Address;
//import com.google.bitcoin.core.AddressFormatException;
//import com.google.bitcoin.core.NetworkParameters;
//import com.google.bitcoin.script.ScriptBuilder;
//import com.google.protobuf.ByteString;
//import de.schildbach.wallet.integration.android.BitcoinIntegration;


public class AHPSDRActivity extends Activity implements SensorEventListener {
    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("SDR: ");
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        height = metrics.heightPixels;
        width = metrics.widthPixels;
        // Create a new GLSurfaceView - this holds the GL Renderer
        mGLSurfaceView = new Waterfall(this, width, height);
        // detect if OpenGL ES 2.0 support exists - if it doesn't, exit.
        if (detectOpenGLES20()) {
            // Tell the surface view we want to create an OpenGL ES 2.0-compatible
            // context, and set an OpenGL ES 2.0-compatible renderer.
            mGLSurfaceView.setEGLContextClientVersion(2);
            mGLSurfaceView.setEGLConfigChooser(true);
            mGLSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            //mGLSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            //mGLSurfaceView.setZOrderOnTop(true);
            renderer = new Renderer(this);
            mGLSurfaceView.setRenderer(renderer);
            mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
        else { // quit if no support - get a better phone! :P
            Context context = getApplicationContext();
            String text = context.getString(R.string.opengl2es_unavailable);
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            this.finish();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        restorePrefs();

        connection=null;

        spectrumView = new SpectrumView(this, width, (int)((float)height/2.5f));
        spectrumView.setRenderer(renderer);
        spectrumView.setGLSurfaceView(mGLSurfaceView);
        spectrumView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT, 1.0f));

        mGLSurfaceView.setSpectrumView(spectrumView);
        mGLSurfaceView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT, 1.0f));

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        ll.addView(spectrumView);
        ll.addView(mGLSurfaceView);
        setContentView(ll);

        filterAdapter = new CustomAdapter(this, R.layout.row, R.id.selection);
        serverAdapter = new CustomAdapter(this, R.layout.row, R.id.selection);
        connectionAdapter = new CustomAdapter(this, R.layout.row, R.id.selection);
    }

    public void restorePrefs() {
        SharedPreferences prefs = getSharedPreferences("aHPSDR", 0);
        band=prefs.getInt("Band", BAND_20);
        filter=prefs.getInt("Filter",FILTER_5);
        mode=prefs.getInt("Mode",MODE_USB);
        band_160_freq = prefs.getLong("band_160_freq", 1850000L);
        band_80_freq = prefs.getLong("band_80_freq", 3850000L);
        band_60_freq = prefs.getLong("band_60_freq", 5371500L);
        band_40_freq = prefs.getLong("band_40_freq", 7050000L);
        band_30_freq = prefs.getLong("band_30_freq", 10135000L);
        band_20_freq = prefs.getLong("band_20_freq", 14200000L);
        band_17_freq = prefs.getLong("band_17_freq", 18130000L);
        band_15_freq = prefs.getLong("band_15_freq", 21270000L);
        band_12_freq = prefs.getLong("band_12_freq", 24910000L);
        band_10_freq = prefs.getLong("band_10_freq", 28500000L);
        band_6_freq = prefs.getLong("band_6_freq", 50200000L);
        band_gen_freq = prefs.getLong("band_gen_freq", 15310000L);
        band_wwv_freq = prefs.getLong("band_wwv_freq", 10000000L);
        long band_default_frequency = 14200000L;
        switch (band){
            case BAND_160:
                band_default_frequency = prefs.getLong("band_160_freq", 1850000L);
                break;
            case BAND_80:
                band_default_frequency = prefs.getLong("band_80_freq", 3850000L);
                break;
            case BAND_60:
                band_default_frequency = prefs.getLong("band_60_freq", 5371500L);
                break;
            case BAND_40:
                band_default_frequency = prefs.getLong("band_40_freq", 7050000L);
                break;
            case BAND_30:
                band_default_frequency = prefs.getLong("band_30_freq", 10135000L);
                break;
            case BAND_20:
                band_default_frequency = prefs.getLong("band_20_freq", 14200000L);
                break;
            case BAND_17:
                band_default_frequency = prefs.getLong("band_17_freq", 18130000L);
                break;
            case BAND_15:
                band_default_frequency = prefs.getLong("band_15_freq", 21270000L);
                break;
            case BAND_12:
                band_default_frequency = prefs.getLong("band_12_freq", 24910000L);
                break;
            case BAND_10:
                band_default_frequency = prefs.getLong("band_10_freq", 28500000L);
                break;
            case BAND_6:
                band_default_frequency = prefs.getLong("band_6_freq", 50200000L);
                break;
            case BAND_GEN:
                band_default_frequency = prefs.getLong("band_gen_freq", 15310000L);
                break;
            case BAND_WWV:
                band_default_frequency = prefs.getLong("band_wwv_freq", 10000000L);
                break;
        }
        frequency = prefs.getLong("Frequency", band_default_frequency);
        filterLow=prefs.getInt("FilterLow",150);
        filterHigh=prefs.getInt("FilterHigh", 2850);
        gain=prefs.getInt("Gain", 5);
        micgain=prefs.getInt("Micgain", 0);
        agc=prefs.getInt("AGC", AGC_LONG);
        fps=prefs.getInt("Fps", FPS_10);
        spectrumAverage=prefs.getInt("SpectrumAverage", 0);
        server=prefs.getString("Server", "qtradio.napan.ca");
        receiver=prefs.getInt("Receiver", 0);
        txUser=prefs.getString("txUser", "");
        txPass=prefs.getString("txPass", "");
        tx_state[0]=prefs.getBoolean("txAllow", false);
        jd_state[0]=prefs.getBoolean("jogSpec", false);
        dsp_state[0]=prefs.getBoolean("NR", false);
        dsp_state[1]=prefs.getBoolean("ANF", false);
        dsp_state[2]=prefs.getBoolean("NB", false);
        dsp_state[3]=prefs.getBoolean("IQ", false);
        dsp_state[4]=prefs.getBoolean("RXDCBlock", false);
        lastFewIpAddresses[0] = prefs.getString("RecentServer0","192.168.2.155");
        lastFewIpAddresses[1] = prefs.getString("RecentServer0","192.168.2.223");
        //lastFewIpAddresses[2] = prefs.getString("RecentServer0","192.168.2.154");
    }

    @Override
    protected void onStop(){
        Log.i("AHPSDRActivity","onStop");
        super.onStop();
        connection.close();
        savePrefs();
    }

    public void savePrefs() {
        boolean isSlave = connection.getHasBeenSlave();
        SharedPreferences prefs = getSharedPreferences("aHPSDR", 0);
        SharedPreferences.Editor editor = prefs.edit();
        if (!isSlave) {
            editor.putInt("Band", band);
            editor.putLong("Frequency", connection.getFrequency());
            editor.putInt("Filter", filter);
            editor.putInt("Mode", connection.getMode());
            editor.putInt("FilterLow", connection.getFilterLow());
            editor.putInt("FilterHigh", connection.getFilterHigh());
            editor.putLong("band_160_freq", band_160_freq);
            editor.putLong("band_80_freq", band_80_freq);
            editor.putLong("band_60_freq", band_60_freq);
            editor.putLong("band_40_freq", band_40_freq);
            editor.putLong("band_30_freq", band_30_freq);
            editor.putLong("band_20_freq", band_20_freq);
            editor.putLong("band_17_freq", band_17_freq);
            editor.putLong("band_15_freq", band_15_freq);
            editor.putLong("band_12_freq", band_12_freq);
            editor.putLong("band_10_freq", band_10_freq);
            editor.putLong("band_6_freq", band_6_freq);
            editor.putLong("band_gen_freq", band_gen_freq);
            editor.putLong("band_wwv_freq", band_wwv_freq);
            editor.putInt("Gain", gain);
            editor.putInt("Micgain", micgain);
            editor.putInt("AGC", agc);
            editor.putInt("Fps", fps);
            editor.putInt("SpectrumAverage", spectrumAverage);
            editor.putString("Server", server);
            editor.putInt("Receiver", receiver);
            editor.putString("txUser", txUser);
            editor.putString("txPass", txPass);
            editor.putBoolean("txAllow", tx_state[0]);
            editor.putBoolean("jogSpec", jd_state[0]);
            editor.putBoolean("NR", dsp_state[0]);
            editor.putBoolean("ANF", dsp_state[1]);
            editor.putBoolean("NB", dsp_state[2]);
            editor.putBoolean("IQ", dsp_state[3]);
            editor.putBoolean("RXDCBlock", dsp_state[4]);
            editor.putString("RecentServer0", lastFewIpAddresses[0]);
            editor.putString("RecentServer1", lastFewIpAddresses[1]);
            //editor.putString("RecentServer2", lastFewIpAddresses[2]);
        }
        editor.apply();
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        spectrumView.setSensors(event.values[0],event.values[1],event.values[2]);
        Log.i("onAccuracyChanged","ACTION_DOWN");
    }

    public boolean onTrackballEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.i("onTouch","ACTION_DOWN");
                spectrumView.setVfoLock();
                mGLSurfaceView.setVfoLock();
                break;
            case MotionEvent.ACTION_MOVE:
                Log.i("onTrackballEvent","ACTION_MOVE");
                spectrumView.scroll(-(int) (event.getX() * 6.0));
                break;
        }
        return true;
    }

    public void onStart() {
        super.onStart();
        Log.i("AHPSDR", "onStart");
        spectrumView.setAverage(-100);
        restorePrefs();
    }

    public void onResume() {
        super.onResume();
        mGLSurfaceView.onResume();
        Log.i("AHPSDR", "onResume");
        //mSensorManager.registerListener(this, mGravity, SensorManager.SENSOR_DELAY_NORMAL);
        if(connection == null) connection = new Connection(server, BASE_PORT+receiver, width);
        setConnectionDefaults(); // To fix: crash at 1384 started here.
        mySetTitle();
        spectrumView.setAverage(-100);
        restorePrefs();
    }

    public void onPause() {
        connection.close();
        mGLSurfaceView.onPause();
        Log.i("AHPSDR", "onPause");
        super.onPause();
        savePrefs();
    }

    public void onDestroy() {
        super.onDestroy();
        Log.i("AHPSDR", "onDestroy");
        connection.close();
        savePrefs();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) { //Where you make adjustments to the menu mid execution...
        for (int menu_number = 0; menu_number < menu.size(); menu_number++) {
            switch (menu.getItem(menu_number).getItemId()) {
                case R.id.action_menu_servers:
                    try {
                        URL updateURL = new URL("http://qtradio.napan.ca/qtradio/qtradio.pl");
                        URLConnection conn = updateURL.openConnection();
                        conn.setUseCaches(false);
                        conn.setConnectTimeout(3000);
                        conn.setReadTimeout(1000);
                        InputStream is = conn.getInputStream();
                        BufferedInputStream bis = new BufferedInputStream(is);
                        ByteArrayBuffer baf = new ByteArrayBuffer(50);

                        int current = 0;
                        while ((current = bis.read()) != -1) {
                            baf.append((byte) current);
                        }
                        bis.close();
                        String html = new String(baf.toByteArray());

                        // need to extract out the servers addresses
                        // look for <tr><td>
                        Vector<String> temp = new Vector<String>();
                        String ip;
                        String call;
                        String clients;
                        int n = 0;
                        int i = 0;
                        int j;
                        serverAdapter.clear();
                        serverAdapter.add(getApplicationContext().getString(R.string.connection_menu_title));
                        for(int l=0; l< lastFewIpAddresses.length; l++) {
                            serverAdapter.add(lastFewIpAddresses[l]);
                        }
                        while ((i = html.indexOf("<tr><td>", i)) != -1) {
                            i += 8;
                            j = html.indexOf("</td>", i);
                            if (j != -1) {
                                ip = html.substring(i, j);
                                temp.add(ip);
                                i = html.indexOf("<td>", j);
                                i += 4;
                                j = html.indexOf("</td>", i);
                                call = html.substring(i, j);
                                i = j + 9;
                                i = html.indexOf("</td>", i);
                                i += 9;
                                i = html.indexOf("</td>", i);
                                i += 9;
                                i = html.indexOf("</td>", i);
                                i += 9;
                                i = html.indexOf("</td>", i);
                                i += 9;
                                j = html.indexOf("lient", i);
                                j--;
                                clients = html.substring(i, j);
                                serverAdapter.add(ip + " (" + call + ")" + " " + clients + "client(s)");
                                i = j;
                                n++;
                            }
                        }
                        //Log.i("servers", html);
                        servers = new CharSequence[n];
                        serverAdapter.setSelection(0);
                        for (i=0; i < lastFewIpAddresses.length; i++) {
                            servers[i]=lastFewIpAddresses[i];
                        }
                        for (i = 0; i < n; i++) {
                            servers[i + lastFewIpAddresses.length] = temp.elementAt(i);
                        }
                        for (i = 0; i < n + lastFewIpAddresses.length; i++){
                            if (servers[i].toString().equals(server)) serverAdapter.setSelection(i+1
                            +lastFewIpAddresses.length);
                        }
                    } catch (Exception e) {
                    }
                    break;
                case R.id.action_menu_filter:
                    try {
                        filters = null;
                        switch (connection.getMode()) {
                            case MODE_LSB:
                            case MODE_USB:
                            case MODE_DSB:
                                filters = ssbFilters;
                                break;
                            case MODE_CWL:
                            case MODE_CWU:
                                filters = cwFilters;
                                break;
                            case MODE_FMN:
                                filters = fmFilters;
                                break;
                            case MODE_AM:
                            case MODE_DIGU:
                            case MODE_DIGL:
                            case MODE_SAM:
                                filters = amFilters;
                                break;
                            case MODE_SPEC:
                            case MODE_DRM:
                                filters = null;
                                break;
                        }
                        filterAdapter.clear();
                        if (filters != null) {
                            for (int k = 0; k < 10; k++) filterAdapter.add(filters[k].toString());
                            filterAdapter.setSelection(filter);
                        }
                    }
                    catch (Exception e) {
                    }
                    break;
                case R.id.action_menu_band:
                    Log.i("rotate_debugging","Line 425");
                    try {
                        if (!connection.getHasBeenSlave()) {        // update band specific default freq
                            switch (connection.getBand()) {
                                case BAND_160:
                                    band_160_freq = connection.getFrequency();
                                    break;
                                case BAND_80:
                                    band_80_freq = connection.getFrequency();
                                    break;
                                case BAND_60:
                                    band_60_freq = connection.getFrequency();
                                    break;
                                case BAND_40:
                                    band_40_freq = connection.getFrequency();
                                    break;
                                case BAND_30:
                                    band_30_freq = connection.getFrequency();
                                    break;
                                case BAND_20:
                                    band_20_freq = connection.getFrequency();
                                    break;
                                case BAND_17:
                                    band_17_freq = connection.getFrequency();
                                    break;
                                case BAND_15:
                                    band_15_freq = connection.getFrequency();
                                    break;
                                case BAND_12:
                                    band_12_freq = connection.getFrequency();
                                    break;
                                case BAND_10:
                                    band_10_freq = connection.getFrequency();
                                    break;
                                case BAND_6:
                                    band_6_freq = connection.getFrequency();
                                    break;
                                case BAND_GEN:
                                    band_gen_freq = connection.getFrequency();
                                    break;
                                case BAND_WWV:
                                    band_wwv_freq = connection.getFrequency();
                                    break;
                            }
                        }
                    }
                    catch (Exception e) {
                    }
                    break;
                case R.id.action_menu_tx_user: // No need to see these menus if TX isn't set.
                case R.id.action_menu_master:
                case R.id.action_menu_mic_gain:
                    try {
                        if (!connection.getAllowTx()) {
                            menu.getItem(menu_number).setVisible(false);
                        } else {
                            menu.getItem(menu_number).setVisible(true);
                        }
                    }
                    catch (Exception e) {
                    }
            }
            spectrumView.setAverage(-100);
        }
        return super.onPrepareOptionsMenu(menu);
    }

   /* @Override
    public void onOptionsMenuClosed(Menu menu) {
        openOptionsMenu();  Rob did this testing.
    }*/

    //	protected Dialog onCreateDialog(final int id) {
//		Dialog dialog;
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Dialog dialog = null;
        AlertDialog.Builder builder;
        switch (item.getItemId()) {
            case R.id.action_menu_quit:
                this.finish();
                break;
		  /*case R.id.action_menu_connection:
                builder = enterServerManually();
			    dialog = builder.create();
			    break;*/
            case R.id.action_menu_servers:
                builder = chooseServer();
                Log.i("servers"," Just finished chooseServer()");
                //dialog = builder.create();
                break;
            case R.id.action_menu_receiver:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.receiver_menu_title);
                builder.setSingleChoiceItems(receivers, receiver,
                        new OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                Log.i("Receiver",Integer.toString(item));
                                mode=connection.getMode();
                                frequency=connection.getFrequency();
                                filterLow=connection.getFilterLow();
                                filterHigh=connection.getFilterHigh();
                                connection.close();
                                receiver=item;
                                connection = new Connection(server, BASE_PORT + receiver,width);
                                setConnectionDefaults();
                                mySetTitle();
                                dialog.dismiss();
                            }
                        });
                dialog = builder.create();
                break;

            case R.id.action_menu_frequency:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.frequency_menu_title);
                final EditText freq = new EditText(this);
                freq.setRawInputType(InputType.TYPE_CLASS_NUMBER); // numeric keypad
                freq.setText(Long.toString(connection.getFrequency()));
                builder.setView(freq);
                builder.setPositiveButton(R.string.ok, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String value = freq.getText().toString().trim();
                        Log.i("Frequency",value);
                        connection.setFrequency(Long.parseLong(value));
                        dialog.dismiss();
                    }
                });
                dialog = builder.create();
                break;
            case R.id.action_menu_band:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.band_menu_title);
                builder.setSingleChoiceItems(bands, band,
                        new OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                //
                                if (item != BAND_RESET) {
                                    band = item;
                                } else {  // BAND_RESET
                                    band_160_freq = 1850000L;
                                    band_80_freq = 3850000L;
                                    band_60_freq = 5371500L;
                                    band_40_freq = 7050000L;
                                    band_30_freq = 10135000L;
                                    band_20_freq = 14200000L;
                                    band_17_freq = 18130000L;
                                    band_15_freq = 21270000L;
                                    band_12_freq = 24910000L;
                                    band_10_freq = 28500000L;
                                    band_6_freq = 50200000L;
                                    band_gen_freq = 15310000L;
                                    band_wwv_freq = 10000000L;
                                }
                                switch (band) {
                                    case BAND_160:
                                        connection.setMode(MODE_LSB);
                                        connection.setFilter(-2850, -150);
                                        connection.setFrequency(band_160_freq);
                                        break;
                                    case BAND_80:
                                        connection.setMode(MODE_LSB);
                                        connection.setFilter(-2850, -150);
                                        connection.setFrequency(band_80_freq);
                                        break;
                                    case BAND_60:
                                        connection.setMode(MODE_LSB);
                                        connection.setFilter(-2850, -150);
                                        connection.setFrequency(band_60_freq);
                                        break;
                                    case BAND_40:
                                        connection.setMode(MODE_LSB);
                                        connection.setFilter(-2850, -150);
                                        connection.setFrequency(band_40_freq);
                                        break;
                                    case BAND_30:
                                        connection.setMode(MODE_USB);
                                        connection.setFilter(150, 2850);
                                        connection.setFrequency(band_30_freq);
                                        break;
                                    case BAND_20:
                                        connection.setMode(MODE_USB);
                                        connection.setFilter(150, 2850);
                                        connection.setFrequency(band_20_freq);
                                        break;
                                    case BAND_17:
                                        connection.setMode(MODE_USB);
                                        connection.setFilter(150, 2850);
                                        connection.setFrequency(band_17_freq);
                                        break;
                                    case BAND_15:
                                        connection.setMode(MODE_USB);
                                        connection.setFilter(150, 2850);
                                        connection.setFrequency(band_15_freq);
                                        break;
                                    case BAND_12:
                                        connection.setMode(MODE_USB);
                                        connection.setFilter(150, 2850);
                                        connection.setFrequency(band_12_freq);
                                        break;
                                    case BAND_10:
                                        connection.setMode(MODE_USB);
                                        connection.setFilter(150, 2850);
                                        connection.setFrequency(band_10_freq);
                                        break;
                                    case BAND_6:
                                        connection.setMode(MODE_USB);
                                        connection.setFilter(150, 2850);
                                        connection.setFrequency(band_6_freq);
                                        break;
                                    case BAND_GEN:
                                        connection.setMode(MODE_SAM);
                                        connection.setFilter(-4000, 4000);
                                        connection.setFrequency(band_gen_freq);
                                        break;
                                    case BAND_WWV:
                                        connection.setMode(MODE_AM);
                                        connection.setFilter(-4000, 4000);
                                        connection.setFrequency(band_wwv_freq);
                                        break;
                                }
                                connection.setBand(band);
                                dialog.dismiss();
                            }
                        });
                dialog = builder.create();
                break;
            case R.id.action_menu_mode:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.mode_menu_title);
                builder.setSingleChoiceItems(modes, connection.getMode(),
                        new OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                mode=item;
                                connection.setMode(mode);
                                filter = FILTER_5;
                                switch (item) {
                                    case MODE_LSB:
                                        connection.setFilter(-2850, -150);
                                        break;
                                    case MODE_USB:
                                        connection.setFilter(150, 2850);
                                        break;
                                    case MODE_DSB:
                                        connection.setFilter(-2600, 2600);
                                        break;
                                    case MODE_CWL:
                                        connection.setFilter(-800, -400);
                                        break;
                                    case MODE_CWU:
                                        connection.setFilter(400, 800);
                                        break;
                                    case MODE_FMN:
                                        connection.setFilter(-2600, 2600);
                                        break;
                                    case MODE_AM:
                                        connection.setFilter(-4000, 4000);
                                        break;
                                    case MODE_DIGU:
                                        connection.setFilter(150, 3450);
                                        break;
                                    case MODE_SPEC:
                                        connection.setFilter(-6000, 6000);
                                        break;
                                    case MODE_DIGL:
                                        connection.setFilter(-3450, -150);
                                        break;
                                    case MODE_SAM:
                                        connection.setFilter(-4000, 4000);
                                        break;
                                    case MODE_DRM:
                                        connection.setFilter(-6000, 6000);
                                        break;
                                }
                                dialog.dismiss();
                            }
                        });
                dialog = builder.create();
                break;
            case R.id.action_menu_filter:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.filter_menu_title);
                filters = null;
                switch (connection.getMode()) {
                    case MODE_LSB:
                    case MODE_USB:
                    case MODE_DSB:
                        filters = ssbFilters;
                        break;
                    case MODE_CWL:
                    case MODE_CWU:
                        filters = cwFilters;
                        break;
                    case MODE_FMN:
                        filters = fmFilters;
                        break;
                    case MODE_AM:
                    case MODE_DIGU:
                    case MODE_DIGL:
                    case MODE_SAM:
                        filters = amFilters;
                        break;
                    case MODE_SPEC:
                    case MODE_DRM:
                        filters = null;
                        break;
                }
                if (filters != null) {
                    filterAdapter.clear();
                    for (int i = 0; i < 10; i++)
                        filterAdapter.add(filters[i].toString());
                    builder.setAdapter(filterAdapter,
                            new OnClickListener() {
                                public void onClick(DialogInterface dialog, int item) {
                                    filter=item;
                                    switch (filter) {
                                        case FILTER_0:
                                            switch (connection.getMode()) {
                                                case MODE_LSB:
                                                    connection.setFilter(-5150, -150);
                                                    break;
                                                case MODE_USB:
                                                    connection.setFilter(150, 5150);
                                                    break;
                                                case MODE_DSB:
                                                    connection.setFilter(5000, 5000);
                                                    break;
                                                case MODE_CWL:
                                                    connection.setFilter(-cwPitch - 500,
                                                            -cwPitch + 500);
                                                    break;
                                                case MODE_CWU:
                                                    connection.setFilter(cwPitch - 500,
                                                            cwPitch + 500);
                                                    break;
                                                case MODE_FMN:
                                                    connection.setFilter(-40000, 40000);
                                                    break;
                                                case MODE_AM:
                                                case MODE_DIGU:
                                                case MODE_DIGL:
                                                case MODE_SAM:
                                                    connection.setFilter(-8000, 8000);
                                                    break;
                                                case MODE_SPEC:
                                                    break;
                                                case MODE_DRM:
                                                    break;
                                            }
                                            break;
                                        case FILTER_1:
                                            switch (connection.getMode()) {
                                                case MODE_LSB:
                                                    connection.setFilter(-4550, -150);
                                                    break;
                                                case MODE_USB:
                                                    connection.setFilter(150, 4550);
                                                    break;
                                                case MODE_DSB:
                                                    connection.setFilter(-4400, 4400);
                                                    break;
                                                case MODE_CWL:
                                                    connection.setFilter(-cwPitch - 400,
                                                            -cwPitch + 400);
                                                    break;
                                                case MODE_CWU:
                                                    connection.setFilter(cwPitch - 400,
                                                            cwPitch + 400);
                                                    break;
                                                case MODE_FMN:
                                                case MODE_AM:
                                                case MODE_DIGU:
                                                case MODE_DIGL:
                                                case MODE_SAM:
                                                    connection.setFilter(-6000, 6000);
                                                    break;
                                                case MODE_SPEC:
                                                    break;
                                                case MODE_DRM:
                                                    break;
                                            }
                                            break;
                                        case FILTER_2:
                                            switch (connection.getMode()) {
                                                case MODE_LSB:
                                                    connection.setFilter(-3950, -150);
                                                    break;
                                                case MODE_USB:
                                                    connection.setFilter(150, 3950);
                                                    break;
                                                case MODE_DSB:
                                                    connection.setFilter(-3800, 3800);
                                                    break;
                                                case MODE_CWL:
                                                    connection.setFilter(-cwPitch - 375,
                                                            -cwPitch + 375);
                                                    break;
                                                case MODE_CWU:
                                                    connection.setFilter(cwPitch - 375,
                                                            cwPitch + 375);
                                                    break;
                                                case MODE_FMN:
                                                case MODE_AM:
                                                case MODE_DIGU:
                                                case MODE_DIGL:
                                                case MODE_SAM:
                                                    connection.setFilter(-5000, 5000);
                                                    break;
                                                case MODE_SPEC:
                                                    break;
                                                case MODE_DRM:
                                                    break;
                                            }
                                            break;
                                        case FILTER_3:
                                            switch (connection.getMode()) {
                                                case MODE_LSB:
                                                    connection.setFilter(-3450, -150);
                                                    break;
                                                case MODE_USB:
                                                    connection.setFilter(150, 3450);
                                                    break;
                                                case MODE_DSB:
                                                    connection.setFilter(-3300, 3300);
                                                    break;
                                                case MODE_CWL:
                                                    connection.setFilter(-cwPitch - 300,
                                                            -cwPitch + 300);
                                                    break;
                                                case MODE_CWU:
                                                    connection.setFilter(cwPitch - 300,
                                                            cwPitch + 300);
                                                    break;
                                                case MODE_FMN:
                                                case MODE_AM:
                                                case MODE_DIGU:
                                                case MODE_DIGL:
                                                case MODE_SAM:
                                                    connection.setFilter(-4000, 4000);
                                                    break;
                                                case MODE_SPEC:
                                                    break;
                                                case MODE_DRM:
                                                    break;
                                            }
                                            break;
                                        case FILTER_4:
                                            switch (connection.getMode()) {
                                                case MODE_LSB:
                                                    connection.setFilter(-3050, -150);
                                                    break;
                                                case MODE_USB:
                                                    connection.setFilter(150, 3050);
                                                    break;
                                                case MODE_DSB:
                                                    connection.setFilter(-2900, 2900);
                                                    break;
                                                case MODE_CWL:
                                                    connection.setFilter(-cwPitch - 250,
                                                            -cwPitch + 250);
                                                    break;
                                                case MODE_CWU:
                                                    connection.setFilter(cwPitch - 250,
                                                            cwPitch + 250);
                                                    break;
                                                case MODE_FMN:
                                                case MODE_AM:
                                                case MODE_DIGU:
                                                case MODE_DIGL:
                                                case MODE_SAM:
                                                    connection.setFilter(-3300, 3300);
                                                    break;
                                                case MODE_SPEC:
                                                    break;
                                                case MODE_DRM:
                                                    break;
                                            }
                                            break;
                                        case FILTER_5:
                                            switch (connection.getMode()) {
                                                case MODE_LSB:
                                                    connection.setFilter(-2850, -150);
                                                    break;
                                                case MODE_USB:
                                                    connection.setFilter(150, 2850);
                                                    break;
                                                case MODE_DSB:
                                                    connection.setFilter(-2700, 2700);
                                                    break;
                                                case MODE_CWL:
                                                    connection.setFilter(-cwPitch - 200,
                                                            -cwPitch + 200);
                                                    break;
                                                case MODE_CWU:
                                                    connection.setFilter(cwPitch - 200,
                                                            cwPitch + 200);
                                                    break;
                                                case MODE_FMN:
                                                case MODE_AM:
                                                case MODE_DIGU:
                                                case MODE_DIGL:
                                                case MODE_SAM:
                                                    connection.setFilter(-2600, 2600);
                                                    break;
                                                case MODE_SPEC:
                                                    break;
                                                case MODE_DRM:
                                                    break;
                                            }
                                            break;
                                        case FILTER_6:
                                            switch (connection.getMode()) {
                                                case MODE_LSB:
                                                    connection.setFilter(-2550, -150);
                                                    break;
                                                case MODE_USB:
                                                    connection.setFilter(150, 2550);
                                                    break;
                                                case MODE_DSB:
                                                    connection.setFilter(-2400, 2400);
                                                    break;
                                                case MODE_CWL:
                                                    connection.setFilter(-cwPitch - 125,
                                                            -cwPitch + 125);
                                                    break;
                                                case MODE_CWU:
                                                    connection.setFilter(cwPitch - 125,
                                                            cwPitch + 125);
                                                    break;
                                                case MODE_FMN:
                                                case MODE_AM:
                                                case MODE_DIGU:
                                                case MODE_DIGL:
                                                case MODE_SAM:
                                                    connection.setFilter(-2000, 2000);
                                                    break;
                                                case MODE_SPEC:
                                                    break;
                                                case MODE_DRM:
                                                    break;
                                            }
                                            break;
                                        case FILTER_7:
                                            switch (connection.getMode()) {
                                                case MODE_LSB:
                                                    connection.setFilter(-2250, -150);
                                                    break;
                                                case MODE_USB:
                                                    connection.setFilter(150, 2250);
                                                    break;
                                                case MODE_DSB:
                                                    connection.setFilter(-2100, 2100);
                                                    break;
                                                case MODE_CWL:
                                                    connection.setFilter(-cwPitch - 50,
                                                            -cwPitch + 50);
                                                    break;
                                                case MODE_CWU:
                                                    connection.setFilter(cwPitch - 50,
                                                            cwPitch + 50);
                                                    break;
                                                case MODE_FMN:
                                                case MODE_AM:
                                                case MODE_DIGU:
                                                case MODE_DIGL:
                                                case MODE_SAM:
                                                    connection.setFilter(-1550, 1550);
                                                    break;
                                                case MODE_SPEC:
                                                    break;
                                                case MODE_DRM:
                                                    break;
                                            }
                                            break;
                                        case FILTER_8:
                                            switch (connection.getMode()) {
                                                case MODE_LSB:
                                                    connection.setFilter(-1950, -150);
                                                    break;
                                                case MODE_USB:
                                                    connection.setFilter(150, 1950);
                                                    break;
                                                case MODE_DSB:
                                                    connection.setFilter(-1800, 1800);
                                                    break;
                                                case MODE_CWL:
                                                    connection.setFilter(-cwPitch - 25,
                                                            -cwPitch + 25);
                                                    break;
                                                case MODE_CWU:
                                                    connection.setFilter(cwPitch - 25,
                                                            cwPitch + 25);
                                                    break;
                                                case MODE_FMN:
                                                case MODE_AM:
                                                case MODE_DIGU:
                                                case MODE_DIGL:
                                                case MODE_SAM:
                                                    connection.setFilter(-1450, 1450);
                                                    break;
                                                case MODE_SPEC:
                                                    break;
                                                case MODE_DRM:
                                                    break;
                                            }
                                            break;
                                        case FILTER_9:
                                            switch (connection.getMode()) {
                                                case MODE_LSB:
                                                    connection.setFilter(-1150, -150);
                                                    break;
                                                case MODE_USB:
                                                    connection.setFilter(150, 1150);
                                                    break;
                                                case MODE_DSB:
                                                    connection.setFilter(-1000, 1000);
                                                    break;
                                                case MODE_CWL:
                                                    connection.setFilter(-cwPitch - 12,
                                                            -cwPitch + 12);
                                                    break;
                                                case MODE_CWU:
                                                    connection.setFilter(cwPitch - 12,
                                                            cwPitch + 12);
                                                    break;
                                                case MODE_FMN:
                                                case MODE_AM:
                                                case MODE_DIGU:
                                                case MODE_DIGL:
                                                case MODE_SAM:
                                                    connection.setFilter(-1000, 1000);
                                                    break;
                                                case MODE_SPEC:
                                                    break;
                                                case MODE_DRM:
                                                    break;
                                            }
                                            break;
                                    }
                                    dialog.dismiss();
                                }
                            });
                    dialog = builder.create();
                }
                break;
            case R.id.action_menu_agc:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.agc_menu_title);
                builder.setSingleChoiceItems(agcs, agc,
                        new OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                //
                                agc=item;
                                connection.setAGC(agc);
                                dialog.dismiss();
                            }
                        });
                dialog = builder.create();
                break;
            case R.id.action_menu_dsp:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.dsp_menu_title);
                builder.setMultiChoiceItems(dsps, dsp_state,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            public void onClick(DialogInterface dialog, int item,
                                                boolean state) {
                                //
                                switch (item) {
                                    case DSP_NR:
                                        connection.setNR(state);
                                        break;
                                    case DSP_ANF:
                                        connection.setANF(state);
                                        break;
                                    case DSP_NB:
                                        connection.setNB(state);
                                        break;
                                    case DSP_IQ:
                                        connection.setIQCorrection(state);
                                        break;
                                    case DSP_RXDC_BLOCK:
                                        connection.setRXDCBlock(state);
                                        break;
                                }
                            }
                        });
                builder.setPositiveButton(R.string.save, new OnClickListener(){
                    public void onClick(DialogInterface dialog, int which){
                        dialog.dismiss();
                    }
                });
                dialog = builder.create();
                break;
            case R.id.action_menu_tx:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.tx_menu_title);
                builder.setMultiChoiceItems(txs, tx_state,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            public void onClick(DialogInterface dialog, int item,
                                                boolean state) {
                                //
                                switch (item) {
                                    case TX_ALLOW:
                                        connection.setAllowTx(state);
                                        break;
                                }
                                dialog.dismiss();
                            }
                        });
                dialog = builder.create();
                break;
            case R.id.action_menu_jog_dir:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.jog_menu_title);
                builder.setMultiChoiceItems(jds, jd_state,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            public void onClick(DialogInterface dialog, int item,
                                                boolean state) {
                                //
                                switch (item) {
                                    case JOG_DIR_SPEC:
                                        spectrumView.setJogButtonDirection(state);
                                        break;
                                }
                                dialog.dismiss();
                            }
                        });
                dialog = builder.create();
                break;
            case R.id.action_menu_tx_user:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.tx_user_menu_title);
                LinearLayout ll = new LinearLayout(this);
                ll.setOrientation(LinearLayout.VERTICAL); // vertical
                final EditText user = new EditText(this);
                final EditText pass = new EditText(this);
                user.setText(txUser);
                pass.setText(txPass);
                ll.addView(user);
                ll.addView(pass);
                builder.setView(ll);
                builder.setPositiveButton("Ok", new OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        txUser = user.getText().toString().trim();
                        txPass = pass.getText().toString().trim();
                        connection.setTxUser(txUser);
                        connection.setTxPass(txPass);
                        dialog.dismiss();
                    }
                });
                dialog = builder.create();
                spectrumView.setAverage(-100);
                break;
            case R.id.action_menu_master:
                connection.setMaster();
                break;
            case R.id.action_menu_gain:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.gain_menu_title);
                builder.setSingleChoiceItems(gains, gain,
                        new OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                gain=item;
                                connection.setGain(gain*10);
                                dialog.dismiss();
                            }
                        });
                dialog = builder.create();
                break;
            case R.id.action_menu_mic_gain:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.mic_gain_menu_title);
                builder.setSingleChoiceItems(micgains, micgain,
                        new OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                micgain=item;
                                connection.setMicGain(micgain);
                                dialog.dismiss();
                            }
                        });
                dialog = builder.create();
                break;
            case R.id.action_menu_fps:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.fps_menu_title);
                builder.setSingleChoiceItems(fpss, fps,
                        new OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                fps=item;
                                connection.getSpectrum_protocol3(fps+1);
                                dialog.dismiss();
                            }
                        });
                dialog = builder.create();
                break;
            case R.id.action_menu_spectrum_average:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.spectrum_average_menu_title);
                builder.setSingleChoiceItems(spectrumAverages, spectrumAverage,
                        new OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                spectrumAverage=item;
                                connection.setSpectrumAverage(item);
                                dialog.dismiss();
                            }
                        });
                dialog = builder.create();
                break;
            case R.id.action_menu_about:
                AboutDialog about = new AboutDialog(this);
                about.setTitle(R.string.about_menu_title);
                about.show();
                break;
	/*	case MENU_DONATE:
			builder = new AlertDialog.Builder(this);
			builder.setTitle("Donate bitcoins");
			builder.setMessage("Please donate bitcoins to help the project team to maintain the servers and upgrade the service.");
			builder.setPositiveButton("DONATE", new DialogInterface.OnClickListener(){
				public void onClick(DialogInterface dialog, int which){
					handleDonate();
					dialog.dismiss();
					}
				});
			builder.setNegativeButton("NOT NOW", new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int which) {
			        dialog.dismiss();
			    	}
				});
			dialog = builder.create();
			break;*/
            case R.id.action_menu_help:
                HelpDialog help = new HelpDialog(this);
                help.setTitle(R.string.help_menu_title);
                help.show();
                break;
            default:
                dialog = null;
                break;
        }
        if(dialog != null) dialog.show();
        return super.onOptionsItemSelected(item);
    }

    private AlertDialog.Builder chooseServer() {
        Dialog dialog;
        AlertDialog.Builder builder;
        builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.server_menu_title);
        builder.setAdapter(serverAdapter,
                new OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        Log.i("selected", "Value of item:" + String.valueOf(item));
                        if (item == 0) {
                            dialog.dismiss();
                            Log.i("servers", "Picked Connect!");
                            enterServerManually();
                        } else {
                            Log.i("servers", servers[item - 1].toString());
                            mode = connection.getMode();
                            connection.close();
                            frequency = connection.getFrequency();
                            band = connection.getBand();
                            filterLow = connection.getFilterLow();
                            filterHigh = connection.getFilterHigh();

                            server = servers[item - 1].toString();
                            connection = new Connection(server, BASE_PORT + receiver, width);
                            if (!setConnectionDefaults()) {
                                dialog.dismiss();
                                Log.i("servers", "Failed to connect.  " + server);
                                Context context = getApplicationContext();
                                int duration = Toast.LENGTH_LONG;
                                Toast toast = Toast.makeText(context, context.getString(R.string.server_hint),
                                        duration);
                                toast.show();
                                toast = Toast.makeText(context, context.getString(R.string.receiver_server_hint),
                                        duration);
                                toast.show();
                                Log.i("servers", "dialog in ");
                                chooseServer();
                            } else {
                                mySetTitle();
                                dialog.dismiss();
                            }
                        }
                    }
                });
        builder.show();
        return builder;
    }
    private AlertDialog.Builder enterServerManually() {
        AlertDialog.Builder builder;

        builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.connection_menu_title);
        final EditText input = new EditText(this);
        input.setText(server);
        builder.setView(input);
        builder.setPositiveButton(R.string.ok, new OnClickListener() {
            public void onClick(DialogInterface ipDialog, int whichButton) {
                String value = input.getText().toString().trim();
                Log.i("Server", value);
                mode = connection.getMode();
                frequency = connection.getFrequency();
                band = connection.getBand();
                filterLow = connection.getFilterLow();
                filterHigh = connection.getFilterHigh();
                boolean sameOldIp = false;
                for (int i=0; i<lastFewIpAddresses.length; i++){
                    if(lastFewIpAddresses[i].contentEquals(server))
                        sameOldIp = true;
                }
                if(!sameOldIp) {
                    lastFewIpAddresses[lastFewIpAddresses.length - 1] = server;
                }
                connection.close();
                server = value;
                receiver = RX_0; // Most servers have only one receiver, RX_0.
                connection = new Connection(server, BASE_PORT + receiver, width);
                setConnectionDefaults();
                mySetTitle();
                ipDialog.dismiss();
            }
        });
        builder.show();
        return builder;
    }

    /**
     * Detects if OpenGL ES 2.0 exists
     * @return true if it does
     */
    private boolean detectOpenGLES20() {
        ActivityManager am =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo info = am.getDeviceConfigurationInfo();
        Log.d("OpenGL Ver:", info.getGlEsVersion());
        return (info.reqGlEsVersion >= 0x20000);
    }

    private boolean setConnectionDefaults(){
        boolean result;
        if (timer != null) timer.cancel();
        connection.setSpectrumView(spectrumView);
        result = connection.connect();
        connection.start(); //Crash here. Fix this.
        connection.sendCommand("q-master");
        connection.sendCommand("setClient SDR(" +this.getString(R.string.version)+")");
        connection.setFrequency(frequency);
        connection.setMode(mode);
        connection.setBand(band);
        connection.setFilter(filterLow, filterHigh);
        connection.setGain(gain * 10);
        connection.setMicGain(micgain);
        connection.setAGC(agc);
        connection.setAllowTx(tx_state[0]);
        spectrumView.setJogButtonDirection(jd_state[0]);
        connection.setTxUser(txUser);
        connection.setTxPass(txPass);
        connection.setNR(dsp_state[0]);
        connection.setANF(dsp_state[1]);
        connection.setNB(dsp_state[2]);
        connection.setIQCorrection(dsp_state[3]);
        connection.setRXDCBlock(dsp_state[4]);
        spectrumView.setConnection(connection);
        mGLSurfaceView.setConnection(connection);
        spectrumView.setAverage(-100);
        connection.setFps(fps);
        connection.setSpectrumAverage(spectrumAverage);
        connection.getSpectrum_protocol3(fps + 1);
        connection.setScaleFactor(1f);
        connection.setHasBeenSlave(false);
        timer = new Timer();
        timer.schedule(new answerTask(), 1000, 1000);
        if (!result){
            Context context = getApplicationContext();
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(context, context.getString(R.string.server_hint),
                    duration);
            toast.show();
            toast = Toast.makeText(context, context.getString(R.string.receiver_server_hint),
                    duration);
            toast.show();
            chooseServer();
            return false;
        } else {
            return true;
        }
    }

    private void mySetTitle(){
        setTitle("SDR: "+server+" (rx"+receiver+") "+qAnswer);
        mHandler.removeCallbacks(updateTitle);
        mHandler.postDelayed(updateTitle, 500);
    }

    private Runnable updateTitle = new Runnable() {
        public void run(){
            setTitle("SDR: "+server+" (rx"+receiver+") "+qAnswer);
        }
    };


    class answerTask extends TimerTask {
        public void run() {
            if (connection != null){
                qAnswer = connection.getAnswer();
                connection.sendCommand("q-master");
                if (connection.getIsSlave()){
                    connection.sendCommand("q-info");
                }
                mHandler.removeCallbacks(updateTitle);
                mHandler.postDelayed(updateTitle, 500);
            }
        }
    }

    public void showToast(final String toast)
    {
        runOnUiThread(new Runnable() {
            public void run()
            {
                Toast.makeText(AHPSDRActivity.this, toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Handle bitcoin Donation
	
	/*private void handleDonate()
	{
		final String address = DONATION_ADDRESSES_MAINNET[0];

		BitcoinIntegration.requestForResult(AHPSDRActivity.this, REQUEST_CODE, address);
	}*/

    private Timer timer;
    private Handler mHandler = new Handler();
    private int width;
    private int height;

    private SensorManager mSensorManager;
    private Sensor mGravity;

    private Connection connection;
    private SpectrumView spectrumView;


    private String lastFewIpAddresses[] = {"192.168.2.155","192.168.2.152"};

    public static final CharSequence[] receivers = { "0", "1", "2", "3", "4","5","6","7" };

    public int receiver = RX_0;

    //Put these in the menu (main_activity_actions.xml) as @id.
    public static final int RX_0 = 0;
    public static final int RX_1 = 1;
    public static final int RX_2 = 2;
    public static final int RX_3 = 3;
    public static final int RX_4 = 4;
    public static final int RX_5 = 5;
    public static final int RX_6 = 6;
    public static final int RX_7 = 7;

    public static final int MENU_QUIT = 0;
    public static final int MENU_BAND = 1;
    public static final int MENU_MODE = 2;
    public static final int MENU_FILTER = 3;
    public static final int MENU_AGC = 4;
    public static final int MENU_DSP = 5;
    public static final int MENU_GAIN = 6;
    public static final int MENU_FPS = 7;
    public static final int MENU_CONNECTION = 8;
    public static final int MENU_RECEIVER = 9;
    public static final int MENU_FREQUENCY = 10;
    public static final int MENU_SERVERS = 11;
    public static final int MENU_TX = 12;
    public static final int MENU_TX_USER = 13;
    public static final int MENU_JOG_DIR = 14;
    public static final int MENU_MASTER = 15;
    public static final int MENU_MIC_GAIN = 16;
    public static final int MENU_SPECTRUM_AVERAGE = 17;
    public static final int MENU_ABOUT = 18;
    public static final int MENU_DONATE = 19;

    public static final CharSequence[] bands = { "160", "80", "60", "40", "30",
            "20", "17", "15", "12", "10", "6", "GEN", "WWV", "Reset" };

    private int band = BAND_20;
    private long frequency=14200000L;

    private long band_160_freq = 1850000L;
    private long band_80_freq = 3850000L;
    private long band_60_freq = 5371500L;
    private long band_40_freq = 7050000L;
    private long band_30_freq = 10135000L;
    private long band_20_freq = 14200000L;
    private long band_17_freq = 18130000L;
    private long band_15_freq = 21270000L;
    private long band_12_freq = 24910000L;
    private long band_10_freq = 28500000L;
    private long band_6_freq = 50200000L;
    private long band_gen_freq = 15310000L;
    private long band_wwv_freq = 10000000L;

    public static final int BAND_160 = 0;
    public static final int BAND_80 = 1;
    public static final int BAND_60 = 2;
    public static final int BAND_40 = 3;
    public static final int BAND_30 = 4;
    public static final int BAND_20 = 5;
    public static final int BAND_17 = 6;
    public static final int BAND_15 = 7;
    public static final int BAND_12 = 8;
    public static final int BAND_10 = 9;
    public static final int BAND_6 = 10;
    public static final int BAND_GEN = 11;
    public static final int BAND_WWV = 12;
    public static final int BAND_RESET = 13;

    private int mode = MODE_USB;

    public static final CharSequence[] modes = { "LSB", "USB", "DSB", "CWL",
            "CWU", "FMN", "AM", "DIGU", "SPEC", "DIGL", "SAM", "DRM" };
    public static final int MODE_LSB = 0;
    public static final int MODE_USB = 1;
    public static final int MODE_DSB = 2;
    public static final int MODE_CWL = 3;
    public static final int MODE_CWU = 4;
    public static final int MODE_FMN = 5;
    public static final int MODE_AM = 6;
    public static final int MODE_DIGU = 7;
    public static final int MODE_SPEC = 8;
    public static final int MODE_DIGL = 9;
    public static final int MODE_SAM = 10;
    public static final int MODE_DRM = 11;

    public static final CharSequence[] agcs = { "OFF", "LONG", "SLOW",
            "MEDIUM", "FAST" };
    private int agc = AGC_LONG;

    public static final int AGC_OFF = 0;
    public static final int AGC_LONG = 1;
    public static final int AGC_SLOW = 2;
    public static final int AGC_MEDIUM = 3;
    public static final int AGC_FAST = 4;

    public static final CharSequence[] dsps = { "NR", "ANF", "NB", "IQ CORRECTION", "RX DC Block" };

    public static final int DSP_NR = 0;
    public static final int DSP_ANF = 1;
    public static final int DSP_NB = 2;
    public static final int DSP_IQ = 3;
    public static final int DSP_RXDC_BLOCK = 4;

    private boolean[] dsp_state = { false, false, false, false, false };

    public static final CharSequence[] txs = { "Allow Tx" };
    public static final CharSequence[] jds = { "> Buttons Move Spectrum" };
    public static final int TX_ALLOW = 0;
    public static final int JOG_DIR_SPEC = 0;
    public boolean[] tx_state = { false };
    public boolean[] jd_state = { false };

    public static final CharSequence[] gains = { "0", "10", "20", "30", "40",
            "50", "60", "70", "80", "90", "100" };

    public int gain = 5;

    public static final CharSequence[] micgains = { "default", "x2", "x4", "x8", "x16", "x32", "x64" };

    public int micgain = 0;

    public static final CharSequence[] fpss = { "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "10", "11", "12", "13", "14", "15" };

    public int fps = FPS_10;

    public static final int FPS_1 = 0;
    public static final int FPS_2 = 1;
    public static final int FPS_3 = 2;
    public static final int FPS_4 = 3;
    public static final int FPS_5 = 4;
    public static final int FPS_6 = 5;
    public static final int FPS_7 = 6;
    public static final int FPS_8 = 7;
    public static final int FPS_9 = 8;
    public static final int FPS_10 = 9;
    public static final int FPS_11 = 10;
    public static final int FPS_12 = 11;
    public static final int FPS_13 = 12;
    public static final int FPS_14 = 13;
    public static final int FPS_15 = 14;

    public static final CharSequence[] spectrumAverages = { "0", "1", "2", "3", "4", "5", "6", "7", "8"};

    private int spectrumAverage = 0;

    public static final CharSequence[] ssbFilters = { "5.0k", "4.4k", "3.8k",
            "3.3k", "2.9k", "2.7k", "2.4k", "2.1k", "1.8k", "1.0k" };
    public static final CharSequence[] cwFilters = { "1.0k", "800", "750",
            "600", "500", "400", "250", "100", "50", "25" };
    public static final CharSequence[] amFilters = { "16.0k", "12.0k", "10.0k",
            "8.0k", "6.6k", "5.2k", "4.0k", "3.1k", "2.9k", "2.0k" };
    public static final CharSequence[] fmFilters = { "80.0k", "12.0k", "10.0k",
            "8.0k", "6.6k", "5.2k", "4.0k", "3.1k", "2.9k", "2.0k" };

    private int filter = FILTER_5;
    private CharSequence[] filters;
    private int filterLow=150;
    private int filterHigh=2875;
    private CustomAdapter filterAdapter;

    public static final int FILTER_0 = 0;
    public static final int FILTER_1 = 1;
    public static final int FILTER_2 = 2;
    public static final int FILTER_3 = 3;
    public static final int FILTER_4 = 4;
    public static final int FILTER_5 = 5;
    public static final int FILTER_6 = 6;
    public static final int FILTER_7 = 7;
    public static final int FILTER_8 = 8;
    public static final int FILTER_9 = 9;

    private int cwPitch = 600;

    private String server = "qtradio.napan.ca";
    private String qAnswer = "";
    private int BASE_PORT = 8000;
    private int port = 8000;
    private CustomAdapter serverAdapter;
    private CustomAdapter connectionAdapter;
    private CharSequence servers[];

    private String txUser = "";
    private String txPass = "";


    private Waterfall mGLSurfaceView = null;
    // The Renderer
    Renderer renderer = null;

    // constants for Bitcoin Donation
    private static final String[] DONATION_ADDRESSES_MAINNET = { "12voruGfjz6LNTQeq8DyXD7U5kRQkfCTTW", "18rB6RA6Tc75sZCRGDyBbrtWd82RrpmEPo" };
    private static final String[] DONATION_ADDRESSES_TESTNET = { "my16ohVdTJK5w5eba6AbTRLatsN3gpGLaK", "mg2Y2CRKK1ACQcNBEUVFRSM7pHeu4kBxuF" };
    private static final int REQUEST_CODE = 0;

    private int selectedIpItem;

}
