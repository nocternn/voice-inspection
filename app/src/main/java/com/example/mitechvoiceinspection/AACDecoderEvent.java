package com.example.mitechvoiceinspection;

import android.util.Log;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;
import net.sourceforge.jaad.adts.ADTSDemultiplexer;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class AACDecoderEvent {
    private static final String LOG_TAG = "AACDecoder";
    private final String aacFilePath;
    private final String pcmFilePath;

    public AACDecoderEvent(String path) {
        this.aacFilePath = path + ".aac";
        this.pcmFilePath = path + ".pcm";
    }


    // Getters
    public String getAACFilePath() {
        return this.aacFilePath;
    }
    public String getPCMFilePath() {
        return this.pcmFilePath;
    }


    public void ConvertAACToPCM(String inputPath, String outputPath) {
        try {
            File aacAudioFile = new File(inputPath);
            FileInputStream inputStream = new FileInputStream(aacAudioFile);
            FileOutputStream outputStream = new FileOutputStream(outputPath);

            ADTSDemultiplexer adts = new ADTSDemultiplexer(inputStream);
            byte[] decoderSpecificInfo = adts.getDecoderSpecificInfo();

            int count = 0;
            byte[] frame, decodedFrame;
            while ((frame = adts.readNextFrame()) != null) {
                decodedFrame = decodeFrame(frame, decoderSpecificInfo);
                outputStream.write(decodedFrame);
            }

            inputStream.close();
            outputStream.close();
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "AAC/PCM file not found");
        } catch (EOFException e) {
            Log.e(LOG_TAG, "End of file");
        } catch (IOException e) {
            Log.e(LOG_TAG, "Could not create ADTS demultiplexer");
            e.printStackTrace();
        }
    }

    private static byte[] decodeFrame(byte[] aacFrame, byte[] info) {
        byte[] audio = null;
        try {
            Decoder dec = new Decoder(info);
            SampleBuffer buf = new SampleBuffer();
            dec.decodeFrame(aacFrame, buf);
            //the aacFrame array contains the AAC frame to decode
            audio = buf.getData(); //this array contains the raw PCM audio data
        } catch (AACException e) {
            Log.e(LOG_TAG, "Could not create decoder");
        }
        return audio;
    }
}
