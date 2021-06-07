package com.example.camera2_demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.SensorListener;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

// IMU
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    // step1: 定义成员变量
    Button button;
    TextureView textureView;
    private static final SparseIntArray ORIRENTATIONS = new SparseIntArray();
    static {
        ORIRENTATIONS.append(Surface.ROTATION_0,90);
        ORIRENTATIONS.append(Surface.ROTATION_90,0);
        ORIRENTATIONS.append(Surface.ROTATION_180,270);
        ORIRENTATIONS.append(Surface.ROTATION_270,180);
    }

    private String camera_id;
    CameraDevice cameraDevice;
    CameraCaptureSession cameraCaptureSession;
    CaptureRequest captureRequest;
    CaptureRequest.Builder captureRequestBuilder;

    private Size imageDimensions;
    private ImageReader imageReader;
    private File file;
    Handler mBackgroundHandler;
    HandlerThread mBackgroundThread;

    // TextViews
    private TextView tvTimestamp;
    private TextView tvFPS;
    private TextView tvSize;
    private TextView tvAcc;
    private TextView tvGyro;
    private TextView tvMag;
    // 传感器
    private SensorManager sensorManager;
    private IMUManager mImuManager; //用于保存IMU数据

    // Camera parameters
    private int imageWidth = 640;
    private int imageHeight = 480;
    private final int framesPerSecond = 30;
    /** Adjustment to auto-exposure (AE) target image brightness in EV */
    private final int aeCompensation = 0;

    private String mSnapshotOutputDir = null;

    // 控制是否录像
    private boolean mRecordingEnabled = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // step2: 获取layout IDs
        textureView = (TextureView)findViewById(R.id.texture_view);
        button = (Button)findViewById(R.id.button_capture);


        assert textureView != null;

        // step3: 声明预览回调
        textureView.setSurfaceTextureListener(textureListener);

        // 设置ID
        tvTimestamp = (TextView) findViewById(R.id.x_Label);
        tvFPS = (TextView) findViewById(R.id.y_Label);
        tvSize = (TextView) findViewById(R.id.z_Label);
        // IMU
        tvAcc = (TextView) findViewById(R.id.acc_Label);
        tvGyro = (TextView) findViewById(R.id.gyro_Label);
        tvMag = (TextView) findViewById(R.id.mag_label);

        // 设置颜色
        tvTimestamp.setTextColor(Color.rgb(0, 255, 0));
        tvFPS.setTextColor(Color.rgb(0, 255, 0));
        tvSize.setTextColor(Color.rgb(0, 255, 0));
        tvAcc.setTextColor(Color.rgb(0, 255, 0));
        tvGyro.setTextColor(Color.rgb(0, 255, 0));
        tvMag.setTextColor(Color.rgb(0, 255, 0));


        // step4: 声明按键拍照
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    takePicture();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor sensora = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(IMUAccListener, sensora, SensorManager.SENSOR_DELAY_GAME);
        Sensor sensorg = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(IMUGyroListener, sensorg, SensorManager.SENSOR_DELAY_GAME);
        Sensor sensorm = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(IMUMagListener, sensorm, SensorManager.SENSOR_DELAY_GAME);

        // 保存图像路径
        mSnapshotOutputDir =  renewOutputDir();

        mRecordingEnabled = false;

        // 保存IMU数据
        if (mImuManager == null) {
            mImuManager = new IMUManager(this);
        }
    }


    private SensorEventListener IMUAccListener = new SensorEventListener() {
        long imu_counter = 0;
        @Override
        public void onSensorChanged(SensorEvent event) {
            float accx = event.values[0];
            float accy = event.values[1];
            float accz = event.values[2];
            if(imu_counter++ % 10 == 0){
                tvAcc.setText(String.format("Acc: %.6s %.6s %.6s", accx, accy, accz));
                if(imu_counter == 1e4)
                    imu_counter = 0;
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    private SensorEventListener IMUGyroListener = new SensorEventListener() {
        long imu_counter = 0;
        @Override
        public void onSensorChanged(SensorEvent event) {
            float gyrox = event.values[0];
            float gyroy = event.values[1];
            float gyroz = event.values[2];
            if(imu_counter++ % 10 == 0) {
                tvGyro.setText(String.format("Gyro: %.6s %.6s %.6s", gyrox, gyroy, gyroz));
                if(imu_counter == 1e4)
                    imu_counter = 0;
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    private SensorEventListener IMUMagListener = new SensorEventListener() {
        long imu_counter = 0;
        @Override
        public void onSensorChanged(SensorEvent event) {
            float magx = event.values[0];
            float magy = event.values[1];
            float magz = event.values[2];
            if(imu_counter++ % 10 == 0) {
                tvMag.setText(String.format("Mag: %.6s %.6s %.6s", magx, magy, magz));
                if(imu_counter == 1e4)
                    imu_counter = 0;
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    // step20: 实现请求权限函数
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 101)
        {
            if(grantResults[0] == PackageManager.PERMISSION_DENIED)
            {
                Toast.makeText(getApplicationContext(),"对不起，相机无权限",Toast.LENGTH_LONG).show();

            }
        }
    }

    // step5: 实现预览回调
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            try {
                // step6: 声明一个打开相机的函数
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        }
    };

    // step9: 实现相机回调
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;

            // step10: 创建相机预览函数
            try {
                createCamerePreview();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    // step11: 实现相机预览函数
    private void createCamerePreview() throws CameraAccessException {
        SurfaceTexture texture = textureView.getSurfaceTexture();
        texture.setDefaultBufferSize(imageDimensions.getWidth(),imageDimensions.getHeight());

        Surface surface = new Surface(texture);

        // 设置ImageReader
        {
            // to set the format of captured images and the maximum number of images that can be accessed in mImageReader
            imageReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.YUV_420_888, 1);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, mBackgroundHandler);
        }

        // 这里请求实现一个预览的功能
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        // 设置帧率
        {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            // get the StepSize of the auto exposure compensation
            Rational aeCompStepSize = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            if(aeCompStepSize == null) {
                Log.e("设置帧率", "Camera doesn't support setting Auto-Exposure Compensation");
                finish();
            }
            Log.d("设置帧率", "AE Compensation StepSize: " + aeCompStepSize);
            int aeCompensationInSteps = aeCompensation * aeCompStepSize.getDenominator() / aeCompStepSize.getNumerator();
            Log.d("设置帧率", "aeCompensationInSteps: " + aeCompensationInSteps );
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeCompensationInSteps);
            // set the camera output frequency to 30Hz
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(framesPerSecond, framesPerSecond));

        }


        captureRequestBuilder.addTarget(surface);
        captureRequestBuilder.addTarget(imageReader.getSurface());

        // step12: 这里实现一个CameraCaptureSession状态回调函数
        List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(surface);
        outputSurfaces.add(imageReader.getSurface());

        cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                if(cameraDevice==null)
                {
                    return;
                }
                cameraCaptureSession =  session;
                // step13: 声明一个更新预览的函数
                try {
                    updatePreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Toast.makeText(getApplicationContext(),"配置改变了",Toast.LENGTH_LONG).show();
            }
        },null);
    }

    private ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        /*
         *  The following method will be called every time an image is ready
         *  be sure to use method acquireNextImage() and then close(), otherwise, the display may STOP
         */
        Long last_timestamp = new Long(0);
        long update_counter;
        @Override
        public void onImageAvailable(ImageReader reader) {

            // get the newest frame
            Image image = reader.acquireNextImage();

            if (image == null) {
                return;
            }

            if (image.getFormat() != ImageFormat.YUV_420_888) {
                Log.e("onImageAvailable", "camera image is in wrong format");
            }

            //RGBA output
            Image.Plane Y_plane = image.getPlanes()[0];
            int Y_rowStride = Y_plane.getRowStride();
            Image.Plane U_plane = image.getPlanes()[1];
            int UV_rowStride = U_plane.getRowStride();
            Image.Plane V_plane = image.getPlanes()[2];

            // pass the current device's screen orientation to the c++ part
            int currentRotation = getWindowManager().getDefaultDisplay().getRotation();
            boolean isScreenRotated = currentRotation != Surface.ROTATION_90;

            int image_height = image.getHeight();
            int image_width = image.getWidth();
            Long timestamp = image.getTimestamp();
            double fps = (double) (1e9 / (timestamp - last_timestamp));

            last_timestamp = timestamp;

            Log.d("正在获取图像回调函数",
                    " Timestamp = " + timestamp
                    + " ImageSize = " + image_width + "x" + image_height
                    + " FPS = " + fps);

            //更新显示
            {
                tvTimestamp.setText(String.format("时间戳: %d", timestamp));
                if((update_counter++)%15 == 0) {
                    tvFPS.setText(String.format("帧率: %.8s", fps));
                }
                tvSize.setText(String.format("尺寸: %dx%d", image_width, image_height));
            }

            //TODO: 保存图像
            {

                String outputFile = mSnapshotOutputDir + File.separator + "data"+ File.separator + timestamp.toString() + ".jpg";
                File dest = new File(outputFile);
                Timber.d("Saving image to %s", outputFile);
                new ImageSaver(image, dest).run();
            }


            image.close();
        }
    };

    // step14: 实现一个更新预览的函数
    private void updatePreview() throws CameraAccessException {
        if(cameraDevice == null)
            return;

        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),null,mBackgroundHandler);
    }


    // step7: 实现打开相机的函数
    private void openCamera() throws CameraAccessException {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        camera_id = manager.getCameraIdList()[0] ;
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera_id);

        // 相机配置参数集合
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        imageDimensions = map.getOutputSizes(SurfaceTexture.class)[0];

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE},101);
            return;
        }
        // step8: 这里声明一个相机回调函数
        manager.openCamera(camera_id,stateCallback,null);

    }

    // step21: TODO: 实现请求权限函数, 其实在实现这个函数之前，已经可以实现预览功能了
    private void takePicture() throws CameraAccessException {
        if(cameraDevice == null) {
            Log.e("takePicture", "相机没有准备好");
            return;
        }

        runOnUiThread(new Runnable()
        {
            public void run()
            {
                CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                CameraCharacteristics characteristics = null;
                try {
                    characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                Size[] JpegSizes = null;
                JpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
                int width = 640;
                int height = 480;
                if (JpegSizes!=null && JpegSizes.length >0)
                {
                    width = JpegSizes[0].getWidth();
                    height = JpegSizes[0].getHeight();
                }
                ImageReader reader = ImageReader.newInstance(width,height,ImageFormat.JPEG,1);
                List<Surface> outputSurface =  new ArrayList<>(2);
                outputSurface.add(reader.getSurface());
                outputSurface.add(new Surface(textureView.getSurfaceTexture()));
                CaptureRequest.Builder captureBuilder = null;
                try {
                    captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                captureBuilder.addTarget(reader.getSurface());
                captureBuilder.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);
                // 指定方向
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,ORIRENTATIONS.get(rotation));
                Long ltimestamp = System.currentTimeMillis();
                String sstimestamp = ltimestamp.toString();
                String jpeg_file_name =  "/sdcard/camera2_demo/data/" + sstimestamp + ".jpeg";
                Log.e("takePicture", jpeg_file_name);
                file = new File(jpeg_file_name);

                return;
            }
        });
    }


    // step15: 重写这个函数
    @Override
    protected void onResume() {
        super.onResume();

        // step18: 声明这个开始线程函数
        startBackgroundThread();

        if(textureView.isAvailable() && mRecordingEnabled)
        {
            try {
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        else {
            textureView.setSurfaceTextureListener(textureListener);
        }
        // IMU写数据
        mImuManager.register();
        updateControls();
    }

    // step19: 实现这个开始线程函数
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera2 Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    protected void onPause() {
        super.onPause();
        // step16: 声明这个停止线程函数
        try {
            stopBackgroundThread();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mImuManager.unregister();
    }
    // step17: 实现这个停止线程函数
    protected void stopBackgroundThread() throws InterruptedException {
        mBackgroundThread.quitSafely();
        mBackgroundThread.join();
        mBackgroundHandler = null;
    }

    public String renewOutputDir() {
        SimpleDateFormat dateFormat =
                new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String folderName = dateFormat.format(new Date());
        String dir1 = getFilesDir().getAbsolutePath();
        String dir2 = Environment.getExternalStorageDirectory().
                getAbsolutePath() + File.separator + "mars_logger";

        String dir3 = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
        Timber.d("dir 1 %s\ndir 2 %s\ndir 3 %s", dir1, dir2, dir3);
        // dir1 and dir3 are always available for the app even the
        // write external storage permission is not granted.
        // "Apparently in Marshmallow when you install with Android studio it
        // never asks you if you should give it permission it just quietly
        // fails, like you denied it. You must go into Settings, apps, select
        // your application and flip the permission switch on."
        // ref: https://stackoverflow.com/questions/40087355/android-mkdirs-not-working
        String outputDir = dir3 + File.separator + folderName;
        String outputDirImage = dir3 + File.separator + folderName + File.separator + "data";
        (new File(outputDir)).mkdirs();
        (new File(outputDirImage)).mkdirs();
        return outputDir;
    }

    public void clickToggleRecording(View view) {
        mRecordingEnabled = !mRecordingEnabled;
        if (mRecordingEnabled) {
            String inertialFile = mSnapshotOutputDir + File.separator + "gyro_accel.csv";
            mImuManager.startRecording(inertialFile);
        } else {
            mImuManager.stopRecording();
        }
        updateControls();
    }
    /**
     * Updates the on-screen controls to reflect the current state of the app.
     */
    private void updateControls() {
        Button toggleRelease = (Button) findViewById(R.id.button_recording);
        int id = mRecordingEnabled ?
                R.string.toggleRecordingOff : R.string.toggleRecordingOn;
        toggleRelease.setText(id);
    }
}