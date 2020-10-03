package com.example.mitechvoiceinspection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.visualizer.amplitude.AudioRecordView;

import org.jtransforms.fft.DoubleFFT_1D;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity implements OnChartValueSelectedListener {
    private static final String LOG_TAG = "AudioRecordTest";
    private Activity activity = this;


    // Requesting permissions
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                    ActivityCompat.requestPermissions(activity, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
                } else {
                    if (!isRecording) {
                        btnRecord.setImageResource(R.drawable.ic_btn_record_stop);
                        startRecording();
                    } else {
                        btnRecord.setImageResource(R.drawable.ic_btn_record_start);
                        stopRecording();
                    }
                    isRecording = !isRecording;
                }
            }
        });
    }


    // Visualizers
    private LineChart visualizerAnalog;
    private LineChart visualizerDigital;

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
        if (!chartType.equals("analog")) {
            axisX.setAxisMaximum((float) SAMPLING_RATE / 2);
        }
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
        return set;
    }


    // Recording audio
    private MediaRecorder recorder = null;
    private Timer recordingTimer;
    private int SAMPLING_RATE = 44100;
    private int ENCODING_BITRATE = 384000;
    private int PERIOD = 100;
    private long tick = 0;

    // Format filename based on current date
    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private static String fileName = null;

    private void startRecording() {
        visualizerAnalog.clearValues();
        visualizerDigital.clearValues();

        // Record to the external cache directory for visibility
        dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        fileName = getExternalFilesDir(null).getAbsolutePath() + "/record_" + dateFormat.format(calendar.getTime()) + ".pcm";

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioSamplingRate(SAMPLING_RATE);
        recorder.setAudioEncodingBitRate(ENCODING_BITRATE);
        recorder.setOutputFile(fileName);

        try {
            recorder.prepare();
            recorder.start();
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "prepare() failed");
        }

        recordingTimer = new Timer();
        tick = 0;
        recordingTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    addEntry(visualizerAnalog, new Entry((float) tick / 1000, recorder.getMaxAmplitude()));
                    tick += PERIOD;
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error at getMaxAmplitude");
                }
            }
        }, 0, PERIOD);
    }


    private double[] rawAudio;
    private DoubleFFT_1D fft;

    private void stopRecording() {
        recorder.stop();
        recorder.release();
        recordingTimer.cancel();
        recorder = null;
        Toast.makeText(getApplicationContext(), "Recording session finished", Toast.LENGTH_LONG).show();

        File audioFile = new File(fileName);
        rawAudio = SoundDataUtils.load16BitPCMRawDataFileAsDoubleArray(audioFile);

        fft = new DoubleFFT_1D(rawAudio.length);
        Thread fftThread = new Thread(new Runnable() {
            @Override
            public void run() {
                fft.realForward(rawAudio);
            }
        });

        float frequencyResolution = SAMPLING_RATE / (float)rawAudio.length;
        int totalSamples = (int)(((float)tick / 1000) * SAMPLING_RATE);
        Log.i(LOG_TAG, Integer.toString(rawAudio.length));
        if (rawAudio.length % 2 == 0) {
            addEntry(visualizerDigital, new Entry(0, (float)rawAudio[0]));
            for (int k = 1; k < rawAudio.length / 2; k++) {
                double real = rawAudio[2*k];
                double imaginary = rawAudio[2*k + 1];
                addEntry(visualizerDigital, new Entry(k * frequencyResolution, (float)Math.sqrt(Math.pow(real, 2) + Math.pow(imaginary, 2))));
            }
            addEntry(visualizerDigital, new Entry(((float)rawAudio.length / 2) * frequencyResolution, (float)rawAudio[1]));
        } else {
            addEntry(visualizerDigital, new Entry(0, (float) rawAudio[0]));
            for (int k = 1; k < (rawAudio.length - 1) / 2; k++) {
                double real = rawAudio[2*k];
                double imaginary = rawAudio[2*k + 1];
                addEntry(visualizerDigital, new Entry(k * frequencyResolution, (float)Math.sqrt(Math.pow(real, 2) + Math.pow(imaginary, 2))));
            }
            addEntry(visualizerDigital, new Entry(((float)(rawAudio.length - 1) / 2) * frequencyResolution, (float)rawAudio[1]));
        }

        Toast.makeText(getApplicationContext(), "Transform finished", Toast.LENGTH_LONG).show();
    }


    @Override
    public void onValueSelected(Entry e, Highlight h) {
        Log.i("Entry selected", e.toString());
    }

    @Override
    public void onNothingSelected() {
        Log.i("Nothing selected", "Nothing selected.");
    }
}