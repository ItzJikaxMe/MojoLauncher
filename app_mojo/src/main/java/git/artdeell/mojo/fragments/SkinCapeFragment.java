package git.artdeell.mojo.fragments;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public class SkinCapeFragment extends Fragment {
    private WebView preview;
    private TextView status;
    private String pendingType = "skin";

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);

    public SkinCapeFragment() {
        super(R.layout.fragment_skin_cape);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        preview = view.findViewById(R.id.skin_cape_preview);
        status = view.findViewById(R.id.skin_cape_status);
        Button skinButton = view.findViewById(R.id.skin_choose_button);
        Button capeButton = view.findViewById(R.id.cape_choose_button);

        WebSettings settings = preview.getSettings();
        settings.setJavaScriptEnabled(true);
        preview.setBackgroundColor(0xFF111022);
        preview.loadDataWithBaseURL("https://cdn.jsdelivr.net/npm/skinview3d@3.4.1/",
                viewerHtml(), "text/html", "UTF-8", null);

        skinButton.setOnClickListener(v -> {
            pendingType = "skin";
            imagePicker.launch("image/png");
        });
        capeButton.setOnClickListener(v -> {
            pendingType = "cape";
            imagePicker.launch("image/png");
        });
    }

    private void onImagePicked(Uri uri) {
        if (uri == null || !isAdded()) return;
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("Cannot open selected image");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024)
                throw new IOException("PNG must be 5 MB or smaller");

            File folder = new File(requireContext().getExternalFilesDir(null), "skins");
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("Cannot create skins folder");
            File target = new File(folder, pendingType + ".png");
            try (FileOutputStream file = new FileOutputStream(target)) { file.write(bytes); }

            String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
            String dataUrl = "data:image/png;base64," + encoded;
            String function = "skin".equals(pendingType) ? "showSkin" : "showCape";
            preview.evaluateJavascript("window." + function + "('" + dataUrl + "')", null);
            status.setText(("skin".equals(pendingType) ? "Skin" : "Cape")
                    + " saved locally: " + target.getName());
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not import PNG: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private String viewerHtml() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#111022}"
                + "canvas{width:100%;height:100%;display:block}</style>"
                + "<script src='bundles/skinview3d.bundle.js'></script></head><body><canvas id='skin'></canvas>"
                + "<script>let viewer=null;function init(){if(!window.skinview3d)return;"
                + "const c=document.getElementById('skin');viewer=new skinview3d.SkinViewer({canvas:c,width:360,height:420});"
                + "viewer.controls.enableRotate=true;viewer.controls.enableZoom=true;"
                + "viewer.animation=new skinview3d.IdleAnimation();}"
                + "window.showSkin=function(data){try{if(!viewer)init();viewer.loadSkin(data);viewer.loadCape(null);}catch(e){}};"
                + "window.showCape=function(data){try{if(!viewer)init();viewer.loadCape(data);}catch(e){}};"
                + "window.addEventListener('load',init);</script></body></html>";
    }

    @Override
    public void onDestroyView() {
        if (preview != null) {
            preview.loadUrl("about:blank");
            preview.destroy();
            preview = null;
        }
        super.onDestroyView();
    }
}
