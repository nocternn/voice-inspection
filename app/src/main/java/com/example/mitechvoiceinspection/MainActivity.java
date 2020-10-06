package com.example.mitechvoiceinspection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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


    // Requesting permissions
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    private boolean isRecording = false;
    private Toolbar toolbar;
//    // Media player
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
                    isRecording = !isRecording;
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
    private int minFreq = 0, maxFreq = 400;

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
            axisX.setAxisMinimum(minFreq);
            axisX.setAxisMaximum(maxFreq);
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
        set.setDrawCircles(false);
        return set;
    }

    private void drawVisualizerFFT() {
        double frequencyResolution = samplingRate / (double)rawAudio.length;
        Log.i(LOG_TAG, Integer.toString(rawAudio.length));
        if (rawAudio.length % 2 == 0) {
//            addEntry(visualizerDigital, new Entry(0, (float)rawAudio[0]));
            for (int k = 1; k < rawAudio.length / 2; k++) {
                double frequency = k * frequencyResolution;

                double real = rawAudio[2*k];
                double imaginary = rawAudio[2*k + 1];
                double magnitude = Math.sqrt(Math.pow(real, 2) + Math.pow(imaginary, 2));

                if (frequency >= minFreq && frequency <= maxFreq && magnitude >= MAGNITUDE_THRESHOLD) {
                    addEntry(visualizerDigital, new Entry((float) frequency, (float) magnitude));
                } else {
                    addEntry(visualizerDigital, new Entry((float)frequency, 0));
                }

            }
//            addEntry(visualizerDigital, new Entry(((float)rawAudio.length / 2) * frequencyResolution, (float)rawAudio[1]));
        } else {
//            addEntry(visualizerDigital, new Entry(0, (float) rawAudio[0]));
            for (int k = 1; k < (rawAudio.length - 1) / 2; k++) {
                double frequency = k * frequencyResolution;

                double real = rawAudio[2*k];
                double imaginary = rawAudio[2*k + 1];
                double magnitude = Math.sqrt(Math.pow(real, 2) + Math.pow(imaginary, 2));

                if (frequency >= minFreq && frequency <= maxFreq && magnitude >= MAGNITUDE_THRESHOLD) {
                    addEntry(visualizerDigital, new Entry((float)frequency, (float)magnitude));
                } else {
                    addEntry(visualizerDigital, new Entry((float)frequency, 0));
                }
            }
//            addEntry(visualizerDigital, new Entry(((float)(rawAudio.length - 1) / 2) * frequencyResolution, (float)rawAudio[1]));
        }
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

    private MediaRecorder recorder;
    private int samplingRate = maxFreq / 2;

    private Timer recordingTimer;
    private int PERIOD = (int) ((1.0 / samplingRate) * 1000);
    private long tick = 0;

    // Format filename based on current date
    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private static String filePath = null;

    private double[] rawAudio = null;
    private DoubleFFT_1D fft;
    private double MAGNITUDE_THRESHOLD = 0.4;

    private void startRecording() {
        visualizerAnalog.clearValues();
        visualizerDigital.clearValues();

        // Record to the external cache directory for visibility
        dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        filePath = getExternalFilesDir(null).getAbsolutePath() + "/record_" + dateFormat.format(calendar.getTime());

        // Initialize recorder
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioSamplingRate(samplingRate);
        recorder.setOutputFile(filePath + ".aac");

        try {
            recorder.prepare();
            recorder.start();
        } catch (IOException e) {
            e.printStackTrace();
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
                    e.printStackTrace();
                }
            }
        }, 0, PERIOD);
    }
    private void stopRecording() {
        isRecording = false;

        recorder.stop();
        recorder.release();
        recordingTimer.cancel();
        recorder = null;
        Toast.makeText(getApplicationContext(), "Recording session finished", Toast.LENGTH_LONG).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                SoundDataUtils.ConvertAACToPCM(filePath);
            }
        });

//        File audioFile = new File(filePath + ".pcm");
//        rawAudio = SoundDataUtils.load16BitPCMRawDataFileAsDoubleArray(audioFile);
//
//        Log.i(LOG_TAG, Integer.toString(rawAudio.length));
//
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                fft = new DoubleFFT_1D(rawAudio.length);
//                fft.realForward(rawAudio);
//            }
//        });
//
//        drawVisualizerFFT();
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
                    samplingRate = maxFreq / 2;
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