package com.shuiyu.octoberrain.readerprobe;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.charset.Charset;
/** 只读合成样本Provider：白名单外路径拒绝，未部署样本以缺失异常暴露，供授权与缺失用例观察。 */
public class FixtureProvider extends ContentProvider {
  /** 允许读取的合成文件名；missing.txt 仅用于缺失用例，部署时不得落盘。 */
  private static final java.util.Set<String> NAMES=new java.util.HashSet<>(java.util.Arrays.asList(
    "utf8.txt","gb.txt","large.txt","pipe.txt","normal.pdf","encrypted.pdf","huge.pdf","corrupt.pdf","scanned.pdf",
    "sample.md","hostile.md","wide.md","sample.zip","sample.epub","drm.epub","fixed.epub","image.png",
    "plain","epubbin","docxzip","pdfbin","missing.txt","multi-a.txt","multi-b.txt"));
  /** 可在首次读取时按需生成的文本样本；其余样本由部署脚本推送到files目录。 */
  private static final java.util.Set<String> GENERATED=new java.util.HashSet<>(java.util.Arrays.asList("utf8.txt","gb.txt","large.txt","pipe.txt"));
  public boolean onCreate(){return true;}
  /** 校验最后路径段在白名单内；不在白名单时抛出安全异常而非读取任意文件。 */
  private String name(Uri uri){String name=uri.getLastPathSegment();if(!NAMES.contains(name))throw new SecurityException("fixture only");return name;}
  /** 返回样本文件；按需生成四个基础文本样本，缺失样本抛出FileNotFoundException。 */
  private File file(String name) throws IOException {
    File f=new File(getContext().getFilesDir(),name);if(f.isFile())return f;
    if(!GENERATED.contains(name))throw new FileNotFoundException("fixture not installed: "+name);
    Charset charset=Charset.forName(name.equals("gb.txt")?"GB18030":"UTF-8");
    StringBuilder sample=new StringBuilder();for(int i=0;i<100;i++)sample.append("十雨阅读器合成样本：中文、标点和段落。0123456789\n");byte[] block=sample.toString().getBytes(charset);
    long target=name.equals("large.txt")?50L*1024*1024:256L*1024;
    try(FileOutputStream out=new FileOutputStream(f)){for(long n=0;n<target;n+=block.length)out.write(block);}return f;
  }
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
    String name=name(uri);MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
    Long size=null;
    if(!name.equals("pipe.txt")){try{size=file(name).length();}catch(IOException ignored){
      /* 缺失样本仍返回名称，让打开阶段产生可观察的缺失异常。 */ }}
    c.addRow(new Object[]{name,size});return c;
  }
  /** 按样本名返回声明MIME；扩展名缺失的合成样本依赖显式映射。 */
  public String getType(Uri uri){
    String n=name(uri);
    if(n.equals("epubbin"))return "application/epub+zip";
    if(n.equals("docxzip"))return "application/zip";
    if(n.equals("pdfbin"))return "application/pdf";
    if(n.equals("plain")||n.equals("missing.txt")||n.startsWith("multi-"))return "text/plain";
    return n.endsWith(".pdf")?"application/pdf":n.endsWith(".md")?"text/markdown":n.endsWith(".png")?"image/png":"text/plain";
  }
  /** 撤销用例中临时拒绝的URI；探针进程重启后清空，避免影响其他用例。 */
  private static final java.util.Set<String> DENIED=new java.util.HashSet<>();
  /** 标记指定URI在本次探针进程中拒绝读取，用于模拟授权撤销后的访问失败。 */
  public static void deny(android.net.Uri uri){DENIED.add(uri.toString());}
  public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
    // 授权撤销用例由探针标记拒绝；读取请求应得到SecurityException，覆盖产品的授权失败提示。
    if(DENIED.contains(uri.toString()))throw new SecurityException("revoked");
    String name=name(uri);android.util.Log.i("ReaderFixture","open type="+name.substring(name.lastIndexOf('.')+1));if(!mode.equals("r"))throw new SecurityException("read only");
    try{
      if(!name.equals("pipe.txt"))return ParcelFileDescriptor.open(file(name),ParcelFileDescriptor.MODE_READ_ONLY);
      ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
      new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){
        byte[] text="管道来源的合成正文。abcdefghijklmnopqrstuvwxyz\n".getBytes("UTF-8");for(int i=0;i<3000;i++)out.write(text);
      }catch(IOException e){}}).start();return pipe[0];
    }catch(IOException e){throw new FileNotFoundException(e.getClass().getSimpleName());}
  }
  public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
  public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
  public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
