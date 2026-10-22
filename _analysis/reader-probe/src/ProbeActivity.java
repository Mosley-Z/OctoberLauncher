package com.shuiyu.octoberrain.readerprobe;
import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.pdf.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.*;
public class ProbeActivity extends Activity {
  private static final String PRODUCT="com.shuiyu.octoberrain";
  private static final String ENTRY=PRODUCT+".feature.reader.source.DocumentEntryActivity";
  private static final String FIXTURE="com.shuiyu.octoberrain.readerprobe.fixture";
  private static final String PRIVATE="com.shuiyu.octoberrain.readerprobe.private";
  private WebView web;
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    if(sendFixture())return;
    String fixture=getIntent().getStringExtra("openFixture");
    if(fixture!=null && java.util.Arrays.asList("sample.md","hostile.md","wide.md","sample.zip","sample.epub","drm.epub","fixed.epub","normal.pdf").contains(fixture)){
      android.net.Uri uri=android.net.Uri.parse("content://com.shuiyu.octoberrain.readerprobe.private/docs/"+fixture);
      if(getIntent().hasExtra("variant"))uri=uri.buildUpon().appendQueryParameter("fixture-run",getIntent().getStringExtra("variant")).build();
      android.content.Intent target=new android.content.Intent(android.content.Intent.ACTION_VIEW)
        .setClassName("com.shuiyu.octoberrain",getIntent().getBooleanExtra("directMain",false)?
          "com.shuiyu.octoberrain.MainActivity":"com.shuiyu.octoberrain.feature.reader.source.DocumentEntryActivity")
        .setDataAndType(uri,fixture.endsWith(".pdf")?"application/pdf":"text/markdown");
      if(getIntent().getBooleanExtra("readGrant",true)) target.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
      if(getIntent().getBooleanExtra("grantFolder",false)){
        target.addFlags(android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        target.setClipData(android.content.ClipData.newRawUri("fixture-root",android.net.Uri.parse("content://com.shuiyu.octoberrain.readerprobe.private/docs/")));
      }
      startActivity(target);finish();return;
    }
    LinearLayout body=new LinearLayout(this); body.setOrientation(1); setContentView(body);
    TextView label=new TextView(this); label.setText("Reader native / WebView compatibility probe"); body.addView(label);
    ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
    log("device api="+Build.VERSION.SDK_INT+" memoryClassMiB="+am.getMemoryClass()+" lowRam="+am.isLowRamDevice()+" abi="+Build.SUPPORTED_ABIS[0]);
    android.content.pm.PackageInfo pkg=WebView.getCurrentWebViewPackage();
    log("webview="+(pkg==null?"none":pkg.packageName+"/"+pkg.versionName));
    web=new WebView(this); body.addView(web,new LinearLayout.LayoutParams(-1,0,1));
    web.getSettings().setJavaScriptEnabled(true); web.getSettings().setAllowFileAccess(false);
    web.getSettings().setBlockNetworkLoads(true);
    web.addJavascriptInterface(new Object(){ @JavascriptInterface public void result(String s){log("web="+s);} },"Probe");
    web.loadDataWithBaseURL("https://reader-probe.invalid/","<html><meta charset='utf-8'><body style='column-width:200px'>Reader probe<script>Probe.result(JSON.stringify({columns:CSS.supports('column-width','200px'),textDecoder:typeof TextDecoder==='function',wasm:typeof WebAssembly==='object',highlights:!!CSS.highlights,arrayAt:typeof Array.prototype.at==='function'}))</script></body></html>","text/html","UTF-8",null);
    new Thread(()->probePdf()).start();
    String office=getIntent().getStringExtra("office");
    if(office!=null){
      web.setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
        android.net.Uri u=request.getUrl();
        if(!"reader-probe.invalid".equals(u.getHost())) return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
        String path=u.getPath().substring(1);
        if(path.contains(".."))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
        try{return new WebResourceResponse(path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":path.endsWith(".html")?"text/html":"application/octet-stream","UTF-8",getAssets().open(path));}
        catch(IOException e){log("asset missing="+path);return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
      }});
      web.loadUrl("https://reader-probe.invalid/"+(office.equals("slides")?"slides.html":"office.html"));
    }
  }
  /**
   * 按 send* 参数构造外部打开意图并发给产品 DocumentEntryActivity。
   *
   * 参数 sendFixture 指定样本名；sendAction 取 VIEW/SEND/SEND_MULTIPLE；
   * sendMime 为空时向自身Provider取类型；readGrant 控制读授权标志；
   * persistable 控制持久授权标志；revokeAfter 在跳转后立即撤销本次授权；
   * uriPrefix 为 fixture 时使用可导出Provider，否则使用私有Provider。
   * 返回 true 表示本次启动已处理，不再进入自带探针界面。
   */
  private boolean sendFixture(){
    String name=getIntent().getStringExtra("sendFixture");if(name==null)return false;
    String action=getIntent().getStringExtra("sendAction");if(action==null)action="VIEW";
    String authority="fixture".equals(getIntent().getStringExtra("uriPrefix"))?FIXTURE:PRIVATE;
    boolean readGrant=getIntent().getBooleanExtra("readGrant",true);
    boolean persistable=getIntent().getBooleanExtra("persistable",false);
    boolean revokeAfter=getIntent().getBooleanExtra("revokeAfter",false);
    android.net.Uri uri=android.net.Uri.parse("content://"+authority+"/docs/"+name);
    android.net.Uri second=android.net.Uri.parse("content://"+authority+"/docs/multi-b.txt");
    String mime=getIntent().getStringExtra("sendMime");if(mime==null)mime=getContentResolver().getType(uri);
    android.content.Intent target=new android.content.Intent();
    if("SEND_MULTIPLE".equals(action)){
      java.util.ArrayList<android.net.Uri> streams=new java.util.ArrayList<>();streams.add(uri);streams.add(second);
      target.setAction(android.content.Intent.ACTION_SEND_MULTIPLE).setType(mime==null?"*/*":mime).putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM,streams);
      target.setClipData(clip(streams,"reader-send-multiple"));
    }else if("SEND".equals(action)){
      if(getIntent().getBooleanExtra("sendBadStream",false)){
        // 放入与 EXTRA_STREAM 约定不符的 Parcelable，验证产品入口的类型防御。
        target.setAction(android.content.Intent.ACTION_SEND).setType("application/pdf").putExtra(android.content.Intent.EXTRA_STREAM,new android.content.Intent());
      }else{
        target.setAction(android.content.Intent.ACTION_SEND).setType(mime==null?"*/*":mime).putExtra(android.content.Intent.EXTRA_STREAM,uri);
        target.setClipData(clip(java.util.Collections.singletonList(uri),"reader-send"));
      }
    }else{
      target.setAction(android.content.Intent.ACTION_VIEW).setDataAndType(uri,mime==null?"*/*":mime);
      target.setClipData(clip(java.util.Collections.singletonList(uri),"reader-view"));
    }
    target.setClassName(PRODUCT,ENTRY);
    // 显式向产品授予读权限，避免同一URI在撤销后重新投递时授权状态不确定。
    if(readGrant){
      target.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
      grantUriPermission(PRODUCT,uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
      if("SEND_MULTIPLE".equals(action))grantUriPermission(PRODUCT,second,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    if(readGrant&&persistable)target.addFlags(android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    boolean launched=false;
    try{startActivity(target);launched=true;}catch(RuntimeException e){log("send error="+e.getClass().getSimpleName());}
    String revoke="skipped";
    int revokeCheck=-2;
    String revokeError="";
    if(revokeAfter&&launched){
      int flags=android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
      try{revokeUriPermission(uri,flags);if("SEND_MULTIPLE".equals(action))revokeUriPermission(second,flags);revoke="ok";}
      catch(RuntimeException e){revoke="failed:"+e.getClass().getSimpleName();}
      // 系统仅撤销授权记录，探针Provider需同步拒绝后续读取，才能观察产品的授权失败提示。
      FixtureProvider.deny(uri);
      // 撤销后查询产品 UID 的实际读授权，用于区分系统保留授权与撤销动作未生效。
      try{
        int uid=getPackageManager().getApplicationInfo(PRODUCT,0).uid;
        revokeCheck=checkUriPermission(uri,android.os.Process.myPid(),uid,flags);
      }catch(Exception e){revokeCheck=-3;revokeError=e.getClass().getSimpleName();}
    }
    log("send={\"fixture\":\""+name+"\",\"action\":\""+action+"\",\"mime\":\""+mime+"\",\"authority\":\""+authority
      +"\",\"readGrant\":"+readGrant+",\"persistable\":"+persistable+",\"revokeAfter\":"+revokeAfter
      +",\"badStream\":"+getIntent().getBooleanExtra("sendBadStream",false)
      +",\"launched\":"+launched+",\"revoke\":\""+revoke+"\",\"revokeCheck\":"+revokeCheck
      +",\"revokeError\":\""+revokeError+"\"}");
    finish();return true;
  }
  /** 用给定URI列表生成携带授权目标的ClipData；API 26起权限按ClipData传递。 */
  private static android.content.ClipData clip(java.util.List<android.net.Uri> uris,String label){
    android.content.ClipData data=android.content.ClipData.newRawUri(label,uris.get(0));
    for(int i=1;i<uris.size();i++)data.addItem(new android.content.ClipData.Item(uris.get(i)));
    return data;
  }
  private void probePdf(){
    long start=SystemClock.elapsedRealtime(); File file=new File(getCacheDir(),"synthetic.pdf");
    try {
      PdfDocument doc=new PdfDocument();
      for(int i=0;i<2;i++){ PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(480,640,i+1).create());
        Paint p=new Paint();p.setColor(Color.BLACK);p.setTextSize(28);page.getCanvas().drawText("Reader probe 012345 page "+i,20,60,p);doc.finishPage(page); }
      try(FileOutputStream out=new FileOutputStream(file)){doc.writeTo(out);}doc.close();
      try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){
        Bitmap b=Bitmap.createBitmap(480,640,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);
        try(PdfRenderer.Page page=renderer.openPage(0)){page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);}
        int[] pixels=new int[480*640];b.getPixels(pixels,0,480,0,0,480,640);int dark=0;for(int value:pixels)if((value&0xffffff)!=0xffffff)dark++;
        try(SharedMemory shared=SharedMemory.create("reader-probe",b.getByteCount())){
          ByteBuffer buffer=shared.mapReadWrite();b.copyPixelsToBuffer(buffer);buffer.rewind();
          Bitmap copy=Bitmap.createBitmap(480,640,Bitmap.Config.ARGB_8888);copy.copyPixelsFromBuffer(buffer);SharedMemory.unmap(buffer);copy.recycle();
        }
        log("pdf pages="+renderer.getPageCount()+" nonwhitePixels="+dark+" sharedMemory=PASS durationMs="+(SystemClock.elapsedRealtime()-start));b.recycle();
      }
    }catch(Throwable e){log("pdf FAIL="+e.getClass().getSimpleName());}finally{file.delete();}
  }
  private static void log(String s){android.util.Log.i("ReaderProbe",s);}
  @Override public void onDestroy(){if(web!=null){web.removeJavascriptInterface("Probe");web.destroy();}super.onDestroy();}
}
