package com.example.mitechvoiceinspection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jtransforms.fft.DoubleFFT_1D;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity implements OnChartValueSelectedListener {
    private static final String LOG_TAG = "MITECH Voice Inspection";


    // Requesting permissions
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    private boolean isRecording = false;
    private Toolbar toolbar;
    // Media player
    private ConstraintLayout playerSheet;
    private BottomSheetBehavior playerSheetBehavior;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        visualizerAnalog = findViewById(R.id.visualizer_analog);
        initializeVisualizer(visualizerAnalog, "analog");

        visualizerDigital = findViewById(R.id.visualizer_digital);
        initializeVisualizer(visualizerDigital, "digital");

        calendar = Calendar.getInstance();

        final FloatingActionButton btnRecord = findViewById(R.id.btn_record);
        btnRecord.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
                } else {
                    if (!isRecording) {
                        btnRecord.setImageResource(R.drawable.ic_btn_record_stop);
                        startRecording();
                    } else {
                        btnRecord.setImageResource(R.drawable.ic_btn_record_start);
                        stopRecording();
                    }
                }
            }
        });


        // Media player
        playerSheet = findViewById(R.id.player_sheet);
        playerSheetBehavior = BottomSheetBehavior.from(playerSheet);

        playerSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        playerSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                        break;
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        break;
                    case BottomSheetBehavior.STATE_DRAGGING:
                        break;
                    case BottomSheetBehavior.STATE_SETTLING:
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        showTextInputDialog(item.getItemId());
        return true;
    }


    // Visualizers
    private LineChart visualizerAnalog;
    private LineChart visualizerDigital;
    private int minFreq = 20, maxFreq = 20000;

    private void initializeVisualizer(LineChart mChart, String chartType) {
        mChart.setOnChartValueSelectedListener(this);

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);

        // Styling
        mChart.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimaryLight));

        Description chartDescription = new Description();
        chartDescription.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.textColor));
        if (chartType.equals("analog")) {
            chartDescription.setText(getResources().getString(R.string.visualizer_analog));
        } else {
            chartDescription.setText(getResources().getString(R.string.visualizer_digital));
        }
        mChart.setDescription(chartDescription);

        LineData data = new LineData();
        data.setValueTextColor(ContextCompat.getColor(getApplicationContext(), R.color.textColor));

        // add empty data
        mChart.setData(data);

        XAxis axisX = mChart.getXAxis();
        axisX.setPosition(XAxis.XAxisPosition.BOTTOM);
//        if (!chartType.equals("analog")) {
//            axisX.setAxisMinimum(minFreq);
//            axisX.setAxisMaximum(maxFreq);
//        }
        axisX.setAvoidFirstLastClipping(true);
        axisX.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.textColor));
        axisX.setEnabled(true);

        YAxis axisYLeft = mChart.getAxisLeft();
//        if (!chartType.equals("analog")) {
//            axisYLeft.setAxisMaximum(1);
//        }
        axisYLeft.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.textColor));
        axisYLeft.setEnabled(true);

        YAxis axisYRight = mChart.getAxisRight();
        axisYRight.setEnabled(false);
    }
    private void addEntry(LineChart mChart, Entry mEntry) {
            LineData data = mChart.getData();

            if (data != null) {
                ILineDataSet set = data.getDataSetByIndex(0);
                // set.addEntry(...); // can be called as well

                if (set == null) {
                    set = createSet();
                    data.addDataSet(set);
                }

                data.addEntry(mEntry, 0);
                data.notifyDataChanged();

                // let the chart know it's data has changed
                mChart.notifyDataSetChanged();

                // limit the number of visible entries
                mChart.setVisibleXRangeMaximum(500);
                // chart.setVisibleYRange(30, AxisDependency.LEFT);

                // move to the latest entry
                mChart.moveViewToX(data.getEntryCount());

                // this automatically refreshes the chart (calls invalidate())
                // chart.moveViewTo(data.getXValCount()-7, 55f,
                // AxisDependency.LEFT);
        }
    }
    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, null);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setLineWidth(2f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        set.setDrawCircles(false);
        return set;
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        Log.i("Entry selected", e.toString());
    }

    @Override
    public void onNothingSelected() {
        Log.i("Nothing selected", "Nothing selected.");
    }


    // Recording audio
    private static final float MAX_REPORTABLE_AMP = 32767f;
    private static final float MAX_REPORTABLE_DB = 90.3087f;
    private static final short THRESHOLD = 350;

    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord recorder = null;
    private Thread recordingThread = null;

    private int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    private int BytesPerElement = 2; // 2 bytes in 16bit format

    private float tick = 0;
    private static final float VISUALIZER_PERIOD = (float)0.005;

    private double[] freqSpectrum;

    // Format filename based on current date
    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private static String filePath = null;

    private void startRecording() {
        visualizerAnalog.clearValues();
        visualizerDigital.clearValues();

        // Record to the external cache directory for visibility
        dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        filePath = getExternalFilesDir(null).getAbsolutePath() + "/record_" + dateFormat.format(calendar.getTime());

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);

        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }
    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;

            freqSpectrum = FFT();
            // draw the frequency spectrum
            for (int i = 0; i < freqSpectrum.length; i++)
                addEntry(visualizerDigital, new Entry(i, (float)freqSpectrum[i]));
        }
    }
    //convert short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }
    //convert byte to short
    private short[] byte2short(byte[] bData) {
        short[] shorts = new short[bData.length / 2];
        for (int i = 0; i < shorts.length; i++) {
            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.put(bData[i * 2]);
            bb.put(bData[(i * 2) + 1]);
            shorts[i] = bb.getShort(0);
        }
        return shorts;
    }
    // Write the output audio in byte
    private void writeAudioDataToFile() {
        short[] sData = new short[BufferElements2Rec];

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(filePath + ".pcm");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        while (isRecording) {
            // gets the voice output from microphone to byte format
            recorder.read(sData, 0, BufferElements2Rec);

            for (int i = 143; i < BufferElements2Rec; i += 220) {
                addEntry(visualizerAnalog, new Entry(tick, sData[i]));
                tick += VISUALIZER_PERIOD;
            }

            try {
                // writes the data to file from buffer
                // stores the voice buffer
                byte[] bData = short2byte(sData);
                os.write(bData, 0, BufferElements2Rec * BytesPerElement);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private double[] FFT() {
        DoubleFFT_1D fft = new DoubleFFT_1D(BufferElements2Rec);
        double[] spectrum = new double[RECORDER_SAMPLERATE / 2];

        try {
            FileInputStream is = new FileInputStream(new File(filePath + ".pcm"));

            byte[] bData = new byte[BufferElements2Rec * BytesPerElement];
            tick = 0;
            while (is.read(bData, 0 , bData.length) != -1) {
                short[] sData = byte2short(bData);

                double[] dData = new double[sData.length];
                for (int i = 0; i < sData.length; i++) {
                    dData[i] = sData[i] / 32768.0;
                }
                fft.realForward(dData);

                float frequencyResolution = RECORDER_SAMPLERATE / (float)dData.length;
                double maxFrequency = 0, maxAmplitude = 0;
                for (int k = 1; k < dData.length / 2; k++) {
                    double frequency = k * frequencyResolution;

                    double real = dData[2*k];
                    double imaginary = dData[2*k + 1];
                    double magnitude = Math.sqrt(Math.pow(real, 2) + Math.pow(imaginary, 2));

                    if (magnitude > maxAmplitude) {
                        maxFrequency = frequency;
                        maxAmplitude = magnitude;
                    }
                }
                spectrum[(int)maxFrequency] = maxAmplitude;
            }
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "PCM file not found");
        } catch (Exception e) {}

        return spectrum;
    }


    // Play audio
    private void playAudio() {
        int bufsize = AudioTrack.getMinBufferSize(44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        AudioTrack audio = new AudioTrack(AudioManager.STREAM_MUSIC,
                RECORDER_SAMPLERATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufsize,
                AudioTrack.MODE_STREAM );
        audio.play();
    }



    // Text input dialog
    private void showTextInputDialog(final int title) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        TextView dialogTitle = new TextView(this);
        dialogTitle.setText("Set frequency");
        dialogTitle.setPadding(20, 20, 20, 20);
        dialogTitle.setTextSize(20F);
        dialogTitle.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary));
        builder.setCustomTitle(dialogTitle);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setPadding(20, 20, 20, 20);
        builder.setView(input);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (title == R.string.toolbar_menu_setMinFreq) {
                    minFreq = Integer.parseInt(input.getText().toString());
                    visualizerDigital.getXAxis().setAxisMinimum(minFreq);
                } else {
                    maxFreq = Integer.parseInt(input.getText().toString());
                    visualizerDigital.getXAxis().setAxisMaximum(maxFreq);
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }
}