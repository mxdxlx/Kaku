package ca.fuwafuwa.kaku.Ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Message;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.util.Pair;

import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import ca.fuwafuwa.kaku.Constants;
import ca.fuwafuwa.kaku.Interfaces.Stoppable;
import ca.fuwafuwa.kaku.MainService;
import ca.fuwafuwa.kaku.Windows.CaptureWindow;

/**
 * Created by 0xbad1d3a5 on 4/16/2016.
 */
public class OcrRunnable implements Runnable, Stoppable {

    private static final String TAG = OcrRunnable.class.getName();

    private MainService mContext;
    private CaptureWindow mCaptureWindow;
    private TessBaseAPI mTessBaseAPI;
    private boolean mRunning = true;
    private BoxParams mBox;
    private Bitmap mBitmap;
    private boolean mHorizontalText;
    private Object mBoxLock = new Object();

    public OcrRunnable(Context context, CaptureWindow captureWindow, boolean horizontalText){
        mContext = (MainService) context;
        mCaptureWindow = captureWindow;
        mBox = null;
        mBitmap = null;
        mHorizontalText = horizontalText;
    }

    @Override
    public void run()
    {
        mTessBaseAPI = new TessBaseAPI();
        String storagePath = mContext.getExternalFilesDir(null).getAbsolutePath();
        Log.e(TAG, storagePath);
        mTessBaseAPI.init(storagePath, "jpn");

        if (!mHorizontalText)
        {
            mTessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK_VERT_TEXT);
        }

        while(mRunning)
        {
            Log.d(TAG, "THREAD STARTING NEW LOOP");

            try
            {
                if (mBox == null) {
                    synchronized (mBoxLock) {

                        if (!mRunning){
                            return;
                        }

                        Log.d(TAG, "WAITING");

                        mBoxLock.wait();

                        if (mBox == null) {
                            continue;
                        }
                    }
                }

                Log.d(TAG, "THREAD STOPPED WAITING");

                long startTime = System.currentTimeMillis();

                if (mBitmap == null){
                    mBox = null;
                    continue;
                }
                saveBitmap(mBitmap);

                mCaptureWindow.showLoadingAnimation();

                mTessBaseAPI.setImage(mBitmap);
                mTessBaseAPI.getHOCRText(0);
                List<OcrChar> ocrChars = processOcrIterator(mTessBaseAPI.getResultIterator());
                mTessBaseAPI.clear();

                if (ocrChars.size() > 0){
                    sendOcrResultToContext(new OcrResult(mBitmap, ocrChars, System.currentTimeMillis() - startTime));
                }
                else{
                    sendToastToContext("No Characters Recognized.");
                }

                mBox = null;
                mBitmap = null;

                mCaptureWindow.stopLoadingAnimation();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        Log.d(TAG, "THREAD STOPPED");
    }

    /**
     * Unblocks the thread and starts OCR
     * @param box Coordinates and size of the area to OCR from the screen
     */
    public void runTess(Bitmap bitmap, BoxParams box){
        synchronized (mBoxLock){
            if (!mRunning){
                return;
            }
            mBitmap = bitmap;
            mBox = box;
            mTessBaseAPI.stop();
            mBoxLock.notify();
            Log.d(TAG, "NOTIFIED");
        }
    }

    /**
     * Cancels OCR recognition in progress if Tesseract has been started
     */
    public void cancel(){
        mTessBaseAPI.stop();
        Log.d(TAG, "CANCELED");
    }

    /**
     * Cancels any OCR recognition in progress and stops any further OCR attempts
     */
    @Override
    public void stop(){
        synchronized (mBoxLock){
            mRunning = false;
            mBox = null;
            mTessBaseAPI.stop();
            mBoxLock.notify();
        }
    }

    private List<OcrChar> processOcrIterator(ResultIterator iterator){

        List<OcrChar> ocrChars = new ArrayList<>();

        if (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL)){
            iterator.begin();
        }
        else {
            return ocrChars;
        }

        do {
            List<Pair<String, Double>> choices = iterator.getSymbolChoicesAndConfidence();
            int[] pos = iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL);
            ocrChars.add(new OcrChar(choices, pos));
        } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL));

        iterator.delete();

        return ocrChars;
    }

    private void sendOcrResultToContext(OcrResult result){
        Message.obtain(mContext.getHandler(), 0, result).sendToTarget();
    }

    private void sendToastToContext(String message){
        Message.obtain(mContext.getHandler(), 0, message).sendToTarget();
    }

    private void saveBitmap(Bitmap bitmap) throws FileNotFoundException {
        saveBitmap(bitmap, "screen");
    }

    private void saveBitmap(Bitmap bitmap, String name) throws FileNotFoundException {
        String fs = String.format("%s/%s/%s_%d.png", mContext.getExternalFilesDir(null).getAbsolutePath(), Constants.SCREENSHOT_FOLDER_NAME, name, System.nanoTime());
        Log.d(TAG, fs);
        FileOutputStream fos = new FileOutputStream(fs);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
    }
}
