package com.xfishs.aplayer.utils;

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
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CaptureFailure;

import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;


public class Dual_camera_121 {
    private static final String TAG = "DualCameraController";

    private static final Integer PERMISSIONS_REQUEST_CODE = 1000;

    // UI 组件
    private TextureView textureView1;
    private TextureView textureView2;
    private Surface surface1;
    private Surface surface2;
    // Camera 相关变量
    private CameraDevice cameraDevice; // 摄像头实例
    private CameraCaptureSession cameraSession; // 捕获会话
    private CaptureRequest.Builder mPreViewBuilder; // 预览请求构造器
    private ImageReader imageReader1; // 第一个摄像头的 ImageReader
    private ImageReader imageReader2; // 第二个摄像头的 ImageReader
    private boolean isCamera1Open = false;
    private boolean isCamera2Open = false;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice1, cameraDevice2;


    private CameraCaptureSession cameraSession1, cameraSession2;
    private String cameraId1, cameraId2;

    // 线程和上下文
    private Activity context;
    private Handler handler; // 用于后台线程的 Handler
    private DualCamera dualCamera; // 双摄像头对象


    public Dual_camera_121(Activity context, TextureView textureView1, TextureView textureView2, DualCamera dualCamera) {
        this.context = context;
        this.textureView1 = textureView1;
        this.textureView2 = textureView2;
        this.dualCamera = dualCamera;
        imageReader1 = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 3);
        imageReader2 = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 3);
        // 设置监听器
        this.textureView2.setSurfaceTextureListener(surfaceTextureListener);
    }

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "Surface 1 is ready");
            openTwoBackCameras();
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


    public void openTwoBackCameras() {
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            int backCameraCount = 0;

            for (String cameraId : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                int cameraFacing = characteristics.get(CameraCharacteristics.LENS_FACING);

                // 查找两个后置相机
                if (cameraFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    if (backCameraCount == 0) {
                        cameraId1 = cameraId; // 第一个后置相机
                    } else if (backCameraCount == 1) {
                        cameraId2 = cameraId; // 第二个后置相机
                    }
                    backCameraCount++;

                    if (backCameraCount == 2) {
                        break;
                    }
                }
            }

            // 确保找到了两个后置相机
            if (backCameraCount < 2) {
                Log.e(TAG, "设备不支持两个后置相机");
                return;
            }

            // 打开第一个后置相机进行预览
            openCamera(cameraId1, 1);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access error", e);
        }
    }

    private void openCamera(String cameraId, int cameraNumber) {
        try {
            HandlerThread thread = new HandlerThread("DualCameraThread");
            thread.start();
            handler = new Handler(thread.getLooper());

            if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions

                return;
            }
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    if (cameraNumber == 1) {
                        cameraDevice1 = camera;
                        isCamera1Open = true;
                        // 设置第一个相机预览
                        SurfaceTexture texture1 = textureView1.getSurfaceTexture();
                        surface1 = new Surface(texture1);
                        startPreview(cameraDevice1, surface1, 1);//使用第一个相机进行预览

                    } else if (cameraNumber == 2) {
                        cameraDevice2 = camera;
                        isCamera2Open = true;
                        SurfaceTexture texture2 = textureView2.getSurfaceTexture();
                        Surface surface2 = new Surface(texture2);
                        startCaptureSession(cameraDevice2, surface2,2); // 调起第二个相机开始拍照
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Error opening camera " + cameraNumber + ": " + error);
                }
            }, handler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error opening camera", e);
        }
    }
    private void review (String cameraId,int cameraNumber){
        try {
            HandlerThread thread = new HandlerThread("DualCameraThread");
            thread.start();
            handler = new Handler(thread.getLooper());

            if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions

                return;
            }
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    if (camera == null) {
                        Log.e(TAG, "Camera device is already closed.");
                        return;
                    }

                    if (cameraNumber == 1) {

                        cameraDevice1 = camera;
                        isCamera1Open = true;
                        // 设置第一个相机预览
                        SurfaceTexture texture1 = textureView1.getSurfaceTexture();
                        surface1 = new Surface(texture1);
                        startPreview(cameraDevice1, surface1, 1);

//                        try {
//                            cameraSession.setRepeatingRequest(mPreViewBuilder.build(), null, handler);
//                        } catch (CameraAccessException e) {
//                            Log.e(TAG, "会话配置失败", e);
//                            throw new RuntimeException(e);
//                        }

                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Error opening camera " + cameraNumber + ": " + error);
                }
            }, handler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error opening camera", e);
        }
    }

    private void startPreview(CameraDevice cameraDevice, Surface surface, int cameraNumber) {
        try {
            mPreViewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreViewBuilder.addTarget(surface);
            List<Surface> surfaces = Arrays.asList(surface, imageReader1.getSurface());
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        cameraSession = session;
                        CaptureRequest previewRequest = mPreViewBuilder.build();
                        cameraSession.setRepeatingRequest(previewRequest, null, handler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Error starting preview", e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Failed to configure the camera capture session");
                }
            }, handler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error starting preview", e);
        }
    }

    public void takePicture() {
        if (isCamera1Open) {
            startCaptureSession(cameraDevice1,surface1,1);//使用第一个相机拍照
        }
    }


    public void closeCamera(CameraDevice cameraDevice, int cameraNumber) {
        try {

            if (cameraDevice != null) {
                cameraDevice.close();
            }
            if(cameraNumber == 1){
                if (cameraSession != null) {
                    cameraSession.close();
                    cameraSession = null;
                }
                cameraDevice1.close();
            }
            else{
                cameraDevice2.close();
            }



        } catch (Exception e) {
            Log.e(TAG, "Error closing camera", e);
        }
    }

    private void startCaptureSession(CameraDevice cameraDevice, Surface surface,int cameraNumber) {
        if (cameraNumber == 1 && (cameraDevice == null || cameraSession == null)) {
            Log.e(TAG, "摄像头未初始化或会话为空");
            return;
        }

      if(cameraNumber == 1){
          try {
              CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);


              CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId1);
              int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);//查找相机设备支持的功能
              //奇怪的是 这里的返回声明他的确支持双摄功能的   坏华子

              CaptureRequest.Builder captureRequest1 = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
              captureRequest1.addTarget(imageReader1.getSurface());
              captureRequest1.set(CaptureRequest.JPEG_ORIENTATION, 90);



              // 在 captureRequest1 中设置图像保存操作
              cameraSession.capture(captureRequest1.build(), new CameraCaptureSession.CaptureCallback() {
                  @Override
                  public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                      super.onCaptureCompleted(session, request, result);
                      Log.d(TAG, "Capture completed.");
                      saveImageToGallery(imageReader1.acquireLatestImage());
                      closeCamera(cameraDevice1,1);
                      openCamera(cameraId2,2);

                  }
              }, handler);

          } catch (CameraAccessException e) {
              Log.e(TAG, "拍照失败", e);
              releaseResources();
          }
      }
      else{
          try{
              CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
              captureRequestBuilder.addTarget(imageReader2.getSurface()); // 设置目标Surface
              captureRequestBuilder.addTarget(new Surface(textureView2.getSurfaceTexture()));

              List<Surface> surfaces = Arrays.asList(
                      imageReader2.getSurface(),
                      new Surface(textureView2.getSurfaceTexture())
              );
              // 配置拍照设置
              captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

              CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

              CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId2);
              float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
              //这里是查看焦距用的 也是我测试时候随便打出来看看



              // 创建拍照会话
              cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                  @Override
                  public void onConfigured(@NonNull CameraCaptureSession session) {
                      try {
                          // 执行拍照请求
                          CaptureRequest captureRequest = captureRequestBuilder.build();
                          session.capture(captureRequest, new CameraCaptureSession.CaptureCallback() {
                              @Override
                              public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                  super.onCaptureCompleted(session, request, result);
                                  Log.d(TAG, "Capture completed.");
                                  // 调用保存图像函数
                                  saveImageToGallery(imageReader2.acquireLatestImage());
                                  isCamera2Open = false;
                                  closeCamera(cameraDevice2,2);//关掉第二个摄像头
                                  review(cameraId1,1);//打开第一个摄像头 并启动预览
//                                  openCamera(cameraId1, 1);//打开第一个摄像头 并启动预览
                              }
                              @Override
                              public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                                          @NonNull CaptureRequest request,
                                                          @NonNull CaptureFailure failure) {
                                  super.onCaptureFailed(session, request, failure);
                                  // 捕获失败时输出错误信息
                                  Log.e(TAG, "Capture failed: " + failure.getReason() + " - " + failure.getRequest().toString());
                              }
                          }, handler);
                      } catch (CameraAccessException e) {
                          Log.e(TAG, "Error during capture", e);
                      }
                  }
                  @Override
                  public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                      Log.e(TAG, "Failed to configure capture session.");
                  }
              }, handler);
          }catch (CameraAccessException e) {
            Log.e(TAG, "Error starting capture session", e);
          }
      }
    }


    // 释放资源
    public void releaseResources() {
        if (cameraSession1 != null) {
            cameraSession1.close();
        }
        if (cameraSession2 != null) {
            cameraSession2.close();
        }
        if (cameraDevice1 != null) {
            cameraDevice1.close();
        }
        if (cameraDevice2 != null) {
            cameraDevice2.close();
        }
        if(imageReader1 !=null){
            imageReader1.close();
        }
        if(imageReader2 !=null){
            imageReader2.close();
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

    public void onDestroyView() {
        releaseResources();
    }
}
