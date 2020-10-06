
package com.example.mitechvoiceinspection;

import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import net.sourceforge.jaad.adts.ADTSDemultiplexer;
import net.sourceforge.jaad.aac.*;

class SoundDataUtils {
    private static final String LOG_TAG = "SoundDataUtils";

    public static double[] load16BitPCMRawDataFileAsDoubleArray(File file) {
        InputStream in = null;
        if (file.isFile()) {
            long size = file.length();
            try {
                in = new FileInputStream(file);
                return readStreamAsDoubleArray(in, size);
            } catch (Exception e) {
            }
        }
        return null;
    }

    public static double[] readStreamAsDoubleArray(InputStream in, long size)
            throws IOException {
        int bufferSize = (int) (size / 2);
        double[] result = new double[bufferSize];
        DataInputStream is = new DataInputStream(in);
        for (int i = 0; i < bufferSize; i++) {
            result[i] = is.readShort() / 32768.0;
        }
        return result;
    }

    public static void ConvertAACToPCM(String path) {
        try {
            File aacAudioFile = new File(path + ".aac");
            FileInputStream inputStream = new FileInputStream(aacAudioFile);
            FileOutputStream outputStream = new FileOutputStream(path + ".pcm");

            ADTSDemultiplexer adts = new ADTSDemultiplexer(inputStream);
            byte[] decoderSpecificInfo = adts.getDecoderSpecificInfo();

            byte[] frame, decodedFrame;
            while ((frame = adts.readNextFrame()) != null) {
                decodedFrame = decodeFrame(frame, decoderSpecificInfo);
                outputStream.write(decodedFrame);
            }

            inputStream.close();
            outputStream.close();
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "AAC/PCM file not found");
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