package com.xfishs.aplayer.utils;

import android.util.SizeF;
import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.hardware.camera2.TotalCaptureResult;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.os.Build;
import android.hardware.camera2.CameraCharacteristics;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.hardware.camera2.CameraManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

public class DualCameraController {
    private static final String TAG = "DualCameraController";

    private static final Integer PERMISSIONS_REQUEST_CODE = 1000;

    // UI 组件
    private TextureView textureView1;
    private TextureView textureView2;

    // Camera 相关变量
    private CameraDevice cameraDevice; // 摄像头实例
    private CameraCaptureSession cameraSession; // 捕获会话
    private CameraCaptureSession vcameraSession;
    private CaptureRequest.Builder mPreViewBuilder; // 预览请求构造器
    private CaptureRequest.Builder vPreViewBuilder;
    private ImageReader imageReader1; // 第一个摄像头的 ImageReader
    private ImageReader imageReader2; // 第二个摄像头的 ImageReader

    private MediaRecorder mediaRecorder1;

    private MediaRecorder mediaRecorder2;
    private File videoFile1;
    private File videoFile2;

    // 线程和上下文
    private Activity context;
    private Handler handler; // 用于后台线程的 Handler
    private DualCamera dualCamera; // 双摄像头对象

    private boolean isCameraOpening = false;

    public DualCameraController(Activity context, TextureView textureView1, TextureView textureView2, DualCamera dualCamera) {
        this.context = context;
        this.textureView1 = textureView1;
        this.textureView2 = textureView2;
        this.dualCamera = dualCamera;

        // 设置监听器
        this.textureView2.setSurfaceTextureListener(surfaceTextureListener);
    }

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(); // SurfaceTexture 准备好后打开摄像头
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    public void openCamera() {
        if (isCameraOpening) {
            Log.e(TAG, "相机正在初始化，跳过此次调用");
            return;
        }
        isCameraOpening = true;

        try {
            HandlerThread thread = new HandlerThread("DualCameraThread");
            thread.start();
            handler = new Handler(thread.getLooper());

            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CODE);
                return;
            }
            manager.openCamera(dualCamera.getLogicCameraId(), cameraOpenCallBack, handler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "打开相机失败", e);
        } finally {
            isCameraOpening = false;
        }
    }

    private CameraDevice.StateCallback cameraOpenCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice device) {
            Log.d(TAG, "相机已打开");
            cameraDevice = device; // 保存 CameraDevice 实例
            config(cameraDevice); // 配置摄像头
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "相机连接断开");
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            Log.e(TAG, "相机打开失败，错误代码：" + error);
            releaseResources(); // 释放资源

            if (error == CameraDevice.StateCallback.ERROR_CAMERA_DEVICE || error == CameraDevice.StateCallback.ERROR_CAMERA_SERVICE) {
                handler.postDelayed(() -> {
                    try {
                        openCamera();
                    } catch (Exception e) {
                        Log.e(TAG, "重新打开相机失败", e);
                    }
                }, 1000);
            }
        }
    };

    public void config(CameraDevice cameraDevice) {
        try {
            List<OutputConfiguration> configurations = new ArrayList<>();
            mPreViewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            SurfaceTexture texture1 = textureView1.getSurfaceTexture();
            Surface surface1 = new Surface(texture1);
            OutputConfiguration configuration =new OutputConfiguration(surface1);
            configuration.setPhysicalCameraId(dualCamera.getPhysicsCameraId1());
            configurations.add(configuration);
            mPreViewBuilder.addTarget(surface1);

            SurfaceTexture texture2 = textureView2.getSurfaceTexture();
            Surface surface2 = new Surface(texture2);
            OutputConfiguration configuration2 =new OutputConfiguration(surface2);
            configuration2.setPhysicalCameraId(dualCamera.getPhysicsCameraId2());
            configurations.add(configuration2);
            mPreViewBuilder.addTarget(surface2);

            imageReader1 = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 3);
            imageReader2 = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 3);
            OutputConfiguration configuration_im1 =new OutputConfiguration(imageReader1.getSurface());
            configuration_im1.setPhysicalCameraId(dualCamera.getPhysicsCameraId1());
            OutputConfiguration configuration_im2 =new OutputConfiguration(imageReader2.getSurface());
            configuration_im2.setPhysicalCameraId(dualCamera.getPhysicsCameraId2());
            configurations.add(configuration_im1);
            configurations.add(configuration_im2);


            // 为每个摄像头初始化 MediaRecorder
            mediaRecorder1 = new MediaRecorder();
            mediaRecorder2 = new MediaRecorder();

            // 设置每个 MediaRecorder 的配置


            Executor executor = command -> handler.post(command);

            SessionConfiguration sessionConfig = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    configurations,
                    executor,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                cameraSession = session;
                                cameraSession.setRepeatingRequest(mPreViewBuilder.build(), null, handler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "会话配置失败", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "配置会话失败");
                        }
                    });
            cameraDevice.createCaptureSession(sessionConfig);
        } catch (CameraAccessException e) {
            Log.e(TAG, "配置相机失败", e);
        }
    }

    public void captureImages() {
        if (cameraDevice == null || cameraSession == null) {
            Log.e(TAG, "摄像头未初始化或会话为空");
            return;
        }

        try {
            if (imageReader1 == null || imageReader2 == null) {
                Log.e(TAG, "ImageReader 未初始化");
                return;
            }

            //检查关于支持camrea2-API所有功能的支持情况
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String[] cameraIdList_1 = manager.getCameraIdList();
            String[] cameraIdList = {dualCamera.getPhysicsCameraId1(), dualCamera.getPhysicsCameraId2()};
            for (String cameraId : cameraIdList) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                if (focalLengths != null) {
                    for (float focalLength : focalLengths) {
                        Log.d("CameraInfo", "Focal Length: " + focalLength + " mm");
                    }
                }

                // 获取传感器物理尺寸
                SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                if (sensorSize != null) {
                    Log.d("CameraInfo", "Sensor Size: " + sensorSize.getWidth() + " x " + sensorSize.getHeight() + " mm");
                }

                // 获取支持的光圈值
                float[] apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
                if (apertures != null) {
                    for (float aperture : apertures) {
                        Log.d("CameraInfo", "Aperture: f/" + aperture);
                    }
                }

                // 计算视野场角 (FOV)
                if (focalLengths != null && focalLengths.length > 0 && sensorSize != null) {
                    float focalLength = focalLengths[0];
                    double horizontalFOV = 2 * Math.atan(sensorSize.getWidth() / (2 * focalLength)) * (180 / Math.PI);
                    double verticalFOV = 2 * Math.atan(sensorSize.getHeight() / (2 * focalLength)) * (180 / Math.PI);
                    Log.d("CameraInfo", "Horizontal FOV: " + horizontalFOV + "°");
                    Log.d("CameraInfo", "Vertical FOV: " + verticalFOV + "°");
                }
            }



//            CameraCharacteristics characteristics = manager.getCameraCharacteristics(dualCamera.getPhysicsCameraId1());
//            int[] availableTemplates = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
//
//            if (Arrays.asList(availableTemplates).contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
//                // 检查是否支持 TEMPLATE_STILL_CAPTURE
//            } else {
//                Log.e(TAG, "Device does not support TEMPLATE_STILL_CAPTURE");
//            }

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                cameraSession.abortCaptures();
            }
            cameraSession.stopRepeating();

            CaptureRequest.Builder captureRequest1 = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequest1.addTarget(imageReader1.getSurface());
            captureRequest1.set(CaptureRequest.JPEG_ORIENTATION, 90);

            CaptureRequest.Builder captureRequest2 = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequest2.addTarget(imageReader2.getSurface());
            captureRequest2.set(CaptureRequest.JPEG_ORIENTATION, 90);

            imageReader1.setOnImageAvailableListener(reader -> saveImageToGalleryAsync(reader.acquireLatestImage()), handler);
            imageReader2.setOnImageAvailableListener(reader -> saveImageToGalleryAsync(reader.acquireLatestImage()), handler);


            cameraSession.capture(captureRequest1.build(), null, handler);
            cameraSession.capture(captureRequest2.build(), null, handler);


            resumePreview();

        } catch (CameraAccessException e) {
            Log.e(TAG, "拍照失败", e);
            releaseResources();
        }
    }

    private void saveImageToGalleryAsync(Image image) {
        new Thread(() -> saveImageToGallery(image)).start();
    }

    private void saveImageToGallery(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/MyApp");
        values.put(MediaStore.Images.Media.IS_PENDING, true);
        values.put(MediaStore.Images.Media.DISPLAY_NAME, System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            try (OutputStream fos = resolver.openOutputStream(uri)) {
                fos.write(bytes);
                fos.flush();
                Log.d(TAG, "图片已保存到: " + uri.toString());
            } catch (IOException e) {
                Log.e(TAG, "图片保存失败", e);
            } finally {
                values.put(MediaStore.Images.Media.IS_PENDING, false);
                resolver.update(uri, values, null, null);
                if (image != null) {
                    image.close();
                }
            }
        } else {
            Log.e(TAG, "无法获取 MediaStore 的 URI");
            if (image != null) {
                image.close();
            }
        }
    }

    public void startDualCameraRecording() {
        if (cameraDevice == null || cameraSession == null) {
            Log.e(TAG, "摄像头未初始化或会话为空");
            return;
        }
//        cameraSession.close();

        try {

            if (mediaRecorder1 == null || mediaRecorder2 == null) {
                Log.e(TAG, "mediaRecorder 未初始化");
                return;
            }


            setupMediaRecorder(mediaRecorder1,1);
            setupMediaRecorder(mediaRecorder2,2);
            List<OutputConfiguration> configurations = new ArrayList<>();
            vPreViewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            SurfaceTexture texture1 = textureView1.getSurfaceTexture();
            Surface surface1 = new Surface(texture1);
            OutputConfiguration configuration =new OutputConfiguration(surface1);
            configuration.setPhysicalCameraId(dualCamera.getPhysicsCameraId1());
            configurations.add(configuration);
            vPreViewBuilder.addTarget(surface1);

            SurfaceTexture texture2 = textureView2.getSurfaceTexture();
            Surface surface2 = new Surface(texture2);
            OutputConfiguration configuration2 =new OutputConfiguration(surface2);
            configuration2.setPhysicalCameraId(dualCamera.getPhysicsCameraId2());
            configurations.add(configuration2);
            vPreViewBuilder.addTarget(surface2);


            OutputConfiguration configuration_v1 =new OutputConfiguration(mediaRecorder1.getSurface());
            configuration_v1.setPhysicalCameraId(dualCamera.getPhysicsCameraId1());
            OutputConfiguration configuration_v2 =new OutputConfiguration(mediaRecorder2.getSurface());
            configuration_v2.setPhysicalCameraId(dualCamera.getPhysicsCameraId2());
            configurations.add(configuration_v1);
            configurations.add(configuration_v2);
            vPreViewBuilder.addTarget(mediaRecorder1.getSurface());
            vPreViewBuilder.addTarget(mediaRecorder2.getSurface());

            Executor executor = command -> handler.post(command);

            SessionConfiguration sessionConfig = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    configurations,
                    executor,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                vcameraSession = session;
                                vcameraSession.setRepeatingRequest(vPreViewBuilder.build(), null, handler);
                                mediaRecorder1.start();
                                mediaRecorder2.start();
                                Log.d(TAG, "双摄像头视频录制已启动");
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "会话配置失败", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "配置会话失败");
                        }
                    });
            cameraDevice.createCaptureSession(sessionConfig);
        } catch (CameraAccessException e) {
            Log.e(TAG, "配置相机失败", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private void setupMediaRecorder(MediaRecorder mediaRecorder, int cameraId) throws IOException {
        // 创建用于视频录制的文件路径
        File videoDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "DualCameraVideos");
        if (!videoDirectory.exists()) {
            videoDirectory.mkdirs();
        }

        // 为每个摄像头创建独立的视频文件
        File videoFile = new File(videoDirectory, "dual_camera_video_" + cameraId + "_" + System.currentTimeMillis() + ".mp4");

        // 保存视频文件路径
        if (cameraId == 1) {
            videoFile1 = videoFile; // 保存第一个摄像头的视频文件路径
        } else {
            videoFile2 = videoFile; // 保存第二个摄像头的视频文件路径
        }

        // 初始化 MediaRecorder
////        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
//        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        mediaRecorder.setVideoSize(1920, 1080); // 视频分辨率
//        mediaRecorder.setVideoFrameRate(30); // 帧率
//        mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
//        mediaRecorder.setOrientationHint(90); // 设置旋转角度（根据设备方向）

        mediaRecorder.reset();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoSize(1920, 1080); // 视频分辨率
        mediaRecorder.setVideoFrameRate(30); // 帧率
        mediaRecorder.setOrientationHint(90); // 设置视频旋转 90 度
        mediaRecorder.prepare();
    }


    private void saveVideoToGallery(Context context, File videoFile) {
        // 使用 MediaScannerConnection 将视频添加到相册
        MediaScannerConnection.scanFile(context, new String[]{videoFile.getAbsolutePath()}, null,
                (path, uri) -> Log.d(TAG, "文件已添加到相册: " + path));
    }
    public void stopDualCameraRecording() {
        try {
            if (mediaRecorder1 != null && mediaRecorder2 != null) {
                // 停止录制
                mediaRecorder1.stop();
                mediaRecorder2.stop();
                mediaRecorder1.reset();
                mediaRecorder2.reset();
                Log.d(TAG, "双摄像头视频录制已停止");

                // 保存视频到相册
                if (videoFile1 != null) {
                    saveVideoToGallery(context, videoFile1); // 保存第一个摄像头的视频
                }
                if (videoFile2 != null) {
                    saveVideoToGallery(context, videoFile2); // 保存第二个摄像头的视频
                }
                if(vcameraSession !=null){
                    vcameraSession.close();
                    vcameraSession = null;
                }

                resumePreview();
            }

        } catch (Exception e) {
            Log.e(TAG, "停止录制失败", e);
        }
    }




    private void resumePreview() {
        try {
            if (cameraSession != null && mPreViewBuilder != null) {
                cameraSession.close();
                config(cameraDevice);
            } else {
                Log.e(TAG, "无法恢复预览：会话或请求为空");
                releaseResources();
                openCamera();
            }
        } catch (Exception e) {
            // 捕获其他异常
            Log.e(TAG, "其他错误", e);
            releaseResources();
            openCamera();
        }
    }


    private void releaseResources() {
        try {
            if (cameraSession != null) {
                try {
                    cameraSession.stopRepeating();
                    cameraSession.abortCaptures();
                } catch (CameraAccessException e) {
                    Log.e(TAG, "停止请求失败", e);
                }
                cameraSession.close();
                cameraSession = null;
            }

            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }

            if (imageReader1 != null) {
                imageReader1.close();
                imageReader1 = null;
            }

            if (imageReader2 != null) {
                imageReader2.close();
                imageReader2 = null;
            }

            Log.d(TAG, "相机资源已成功释放");
        } catch (Exception e) {
            Log.e(TAG, "释放相机资源失败", e);
        }
    }

    public void onDestroyView() {
        releaseResources();
    }
}