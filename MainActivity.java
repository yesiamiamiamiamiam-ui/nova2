package com.nova.gallery;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQ_MEDIA = 41;
    private final int BG = Color.rgb(16,16,20), PANEL = Color.rgb(27,27,34);
    private final int TEXT = Color.rgb(245,244,250), MUTED = Color.rgb(157,155,170), ACCENT = Color.rgb(184,167,255);
    private final ArrayList<MediaItem> all = new ArrayList<>();
    private final ArrayList<MediaItem> shown = new ArrayList<>();
    private LinearLayout root, top, bottom;
    private GridView grid;
    private TextView title, count, empty;
    private String tab = "Photos", albumFilter = null, search = "";
    private boolean permissionAsked = false;

    static class MediaItem {
        Uri uri; String name, bucket; boolean video; long date;
        MediaItem(Uri u, String n, String b, boolean v, long d) { uri=u; name=n; bucket=b; video=v; date=d; }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildShell();
        requestMediaAccess();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }
    private GradientRound background(int color, int radius) {
        GradientRound d = new GradientRound(color, dp(radius)); return d;
    }
    private void buildShell() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(12), dp(16), 0);
        setContentView(root);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(8), 0, dp(16));
        TextView brand = label("NOVA", 25, TEXT, true);
        TextView sub = label("  GALLERY", 13, ACCENT, true);
        header.addView(brand); header.addView(sub);
        View spacer = new View(this); header.addView(spacer, new LinearLayout.LayoutParams(0,1,1));
        Button searchBtn = new Button(this); searchBtn.setText("⌕"); searchBtn.setTextSize(23);
        searchBtn.setTextColor(TEXT); searchBtn.setBackground(background(PANEL, 18));
        header.addView(searchBtn, new LinearLayout.LayoutParams(dp(46), dp(42)));
        searchBtn.setOnClickListener(v -> showSearch());
        root.addView(header);

        LinearLayout heading = new LinearLayout(this); heading.setOrientation(LinearLayout.VERTICAL);
        title = label("Your memories", 22, TEXT, true);
        count = label("Your photos and videos, in one place", 13, MUTED, false);
        heading.addView(title); heading.addView(count);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2); hp.bottomMargin=dp(16);
        root.addView(heading,hp);

        grid = new GridView(this); grid.setNumColumns(3); grid.setHorizontalSpacing(dp(5)); grid.setVerticalSpacing(dp(5));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); grid.setSelector(android.R.color.transparent);
        root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        empty = label("Your gallery is waiting here.\nAllow photo access to see your media.", 15, MUTED, false);
        empty.setGravity(Gravity.CENTER); empty.setVisibility(View.GONE);
        root.addView(empty,new LinearLayout.LayoutParams(-1,0,1));

        bottom = new LinearLayout(this); bottom.setGravity(Gravity.CENTER); bottom.setPadding(0,dp(10),0,dp(14));
        bottom.setBackgroundColor(BG);
        root.addView(bottom,new LinearLayout.LayoutParams(-1,-2));
        renderBottom();
    }
    private void renderBottom() {
        bottom.removeAllViews();
        String[] tabs={"Photos","Albums","Videos"};
        for (String t:tabs) {
            LinearLayout item=new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
            TextView icon=label(t.equals("Photos")?"▦":t.equals("Albums")?"▣":"▷",21,tab.equals(t)?ACCENT:MUTED,true);
            TextView name=label(t,11,tab.equals(t)?ACCENT:MUTED,true);
            item.addView(icon); item.addView(name);
            item.setOnClickListener(v->{tab=t; albumFilter=null; search=""; renderBottom(); refresh();});
            bottom.addView(item,new LinearLayout.LayoutParams(0,dp(48),1));
        }
    }
    private void requestMediaAccess() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= 34) perms = new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        else if (Build.VERSION.SDK_INT >= 33) perms = new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO};
        else perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        ArrayList<String> missing=new ArrayList<>();
        for(String p:perms) if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED) missing.add(p);
        if(missing.isEmpty()) loadMedia();
        else requestPermissions(missing.toArray(new String[0]),REQ_MEDIA);
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if(r==REQ_MEDIA) { loadMedia(); }
    }
    private void loadMedia() {
        all.clear();
        if(Build.VERSION.SDK_INT>=33) {
            if(checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED ||
               (Build.VERSION.SDK_INT>=34 && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED))
                query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,false);
            if(checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED)
                query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,true);
        } else if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED) {
            query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,false); query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,true);
        }
        refresh();
    }
    private void query(Uri collection, boolean video) {
        String[] projection={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.DATE_ADDED,MediaStore.Images.Media.BUCKET_DISPLAY_NAME};
        try(Cursor c=getContentResolver().query(collection,projection,null,null,MediaStore.MediaColumns.DATE_ADDED+" DESC")) {
            if(c==null)return;
            int id=c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID), nm=c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int dt=c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED), bk=c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
            while(c.moveToNext()) {
                long mediaId=c.getLong(id); String name=nm>=0?c.getString(nm):"Media";
                String bucket=bk>=0?c.getString(bk):"Other"; long date=dt>=0?c.getLong(dt):0;
                all.add(new MediaItem(ContentUris.withAppendedId(collection,mediaId),name,bucket,video,date));
            }
        } catch(Exception ignored) {}
    }
    private void refresh() {
        shown.clear();
        if("Albums".equals(tab)) {
            title.setText("Albums"); count.setText("Browse folders on your device");
            renderAlbums(); return;
        }
        for(MediaItem m:all) {
            if("Videos".equals(tab) && !m.video) continue;
            if("Photos".equals(tab) && m.video) continue;
            if(albumFilter!=null && !albumFilter.equals(m.bucket)) continue;
            if(!search.isEmpty() && !m.name.toLowerCase().contains(search.toLowerCase()) && !m.bucket.toLowerCase().contains(search.toLowerCase())) continue;
            shown.add(m);
        }
        title.setText(albumFilter==null?("Videos".equals(tab)?"Your videos":"Your memories"):albumFilter);
        count.setText(shown.size()+" items");
        grid.setVisibility(View.VISIBLE); empty.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);
        if(shown.isEmpty()) grid.setVisibility(View.GONE);
        grid.setAdapter(new MediaAdapter());
    }
    private void renderAlbums() {
        LinkedHashMap<String,Integer> albums=new LinkedHashMap<>();
        for(MediaItem m:all) albums.put(m.bucket,albums.containsKey(m.bucket)?albums.get(m.bucket)+1:1);
        ArrayList<String> names=new ArrayList<>(albums.keySet());
        grid.setVisibility(names.isEmpty()?View.GONE:View.VISIBLE); empty.setVisibility(names.isEmpty()?View.VISIBLE:View.GONE);
        count.setText(names.size()+" folders");
        grid.setAdapter(new BaseAdapter(){
            public int getCount(){return names.size();} public Object getItem(int p){return names.get(p);} public long getItemId(int p){return p;}
            public View getView(int p,View old,ViewGroup parent){
                LinearLayout card=new LinearLayout(MainActivity.this); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER);
                card.setBackground(background(PANEL,14)); card.setPadding(dp(5),dp(14),dp(5),dp(14));
                TextView ico=label("▣",30,ACCENT,true); TextView n=label(names.get(p),12,TEXT,true); n.setGravity(Gravity.CENTER);
                TextView c=label(albums.get(names.get(p))+" items",10,MUTED,false);
                card.addView(ico); card.addView(n); card.addView(c);
                card.setOnClickListener(v->{albumFilter=names.get(p);tab="Photos";renderBottom();refresh();});
                return card;
            }
        });
    }
    private class MediaAdapter extends BaseAdapter {
        public int getCount(){return shown.size();} public Object getItem(int p){return shown.get(p);} public long getItemId(int p){return p;}
        public View getView(int p,View old,ViewGroup parent) {
            FrameLayout frame=new FrameLayout(MainActivity.this);
            ImageView image=new ImageView(MainActivity.this); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image,new FrameLayout.LayoutParams(-1,dp(116)));
            MediaItem m=shown.get(p);
            try {
                if(Build.VERSION.SDK_INT>=29) image.setImageBitmap(getContentResolver().loadThumbnail(m.uri,new android.util.Size(dp(180),dp(180)),null));
                else { try(InputStream in=getContentResolver().openInputStream(m.uri)){image.setImageBitmap(BitmapFactory.decodeStream(in));} }
            } catch(Exception e) { image.setBackground(background(PANEL,8)); }
            if(m.video) {
                TextView play=label("▶",14,Color.WHITE,true); play.setGravity(Gravity.CENTER); play.setBackground(background(0x77000000,30));
                FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER); frame.addView(play,pp);
            }
            frame.setOnClickListener(v->openViewer(m));
            return frame;
        }
    }
    private void openViewer(MediaItem m) {
        FrameLayout page=new FrameLayout(this); page.setBackgroundColor(Color.BLACK);
        ImageView iv=new ImageView(this); iv.setBackgroundColor(Color.BLACK); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        page.addView(iv,new FrameLayout.LayoutParams(-1,-1));
        try { if(Build.VERSION.SDK_INT>=29) iv.setImageBitmap(getContentResolver().loadThumbnail(m.uri,new android.util.Size(1200,1600),null));
            else {try(InputStream in=getContentResolver().openInputStream(m.uri)){iv.setImageBitmap(BitmapFactory.decodeStream(in));}} } catch(Exception ignored){}
        TextView close=label("‹",36,Color.WHITE,true); close.setGravity(Gravity.CENTER); close.setBackground(background(0x88000000,30));
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.START); cp.setMargins(dp(16),dp(28),0,0); page.addView(close,cp);
        close.setOnClickListener(v->{setContentView(root);});
        TextView filename=label(m.name,13,Color.WHITE,true); filename.setBackgroundColor(0x77000000); filename.setPadding(dp(16),dp(12),dp(16),dp(12));
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM); page.addView(filename,fp);
        setContentView(page);
    }
    private void showSearch() {
        android.widget.EditText input=new android.widget.EditText(this); input.setSingleLine(true); input.setHint("Search photos and folders");
        input.setTextColor(TEXT); input.setHintTextColor(MUTED); input.setBackground(background(PANEL,14)); input.setPadding(dp(14),0,dp(14),0);
        new android.app.AlertDialog.Builder(this).setTitle("Search your gallery").setView(input)
          .setPositiveButton("Search",(d,w)->{search=input.getText().toString();tab="Photos";albumFilter=null;renderBottom();refresh();})
          .setNegativeButton("Cancel",null).show();
    }
    @Override public void onBackPressed() {
        if(albumFilter!=null){albumFilter=null;tab="Albums";renderBottom();refresh();}
        else if(!"Photos".equals(tab)){tab="Photos";renderBottom();refresh();}
        else super.onBackPressed();
    }
    static class GradientRound extends android.graphics.drawable.GradientDrawable {
        GradientRound(int c,int r){setColor(c);setCornerRadius(r);}
    }
}
