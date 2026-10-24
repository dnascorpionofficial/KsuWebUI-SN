package io.github.a13e300.ksuwebui;

import android.util.Log;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.webkit.WebViewAssetLoader;

import com.topjohnwu.superuser.nio.FileSystemManager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Serves files of a module's {@code webroot} to the WebView, reading them through the root
 * file system service, plus the generated {@code internal/insets.css} and
 * {@code internal/colors.css}.
 * <p>
 * Any file under the given directory can be requested by the page, so only pass a module's
 * own webroot.
 */
public final class RemoteFsPathHandler implements WebViewAssetLoader.PathHandler {
    private static final String TAG = "FsServicePathHandler";

    /**
     * Default value to be used as MIME type if guessing MIME type failed.
     */
    public static final String DEFAULT_MIME_TYPE = "text/plain";

    /** Directories that must never be exposed, they often contain sensitive information. */
    private static final String[] FORBIDDEN_DATA_DIRS =
            new String[] {"/data/data", "/data/system"};

    @NonNull
    private final File mDirectory;

    private final FileSystemManager mFs;

    private final InsetsSupplier mInsetsSupplier;
    private final OnInsetsRequestedListener mOnInsetsRequestedListener;

    public interface InsetsSupplier {
        @NonNull
        Insets get();
    }

    public interface OnInsetsRequestedListener {
        void onInsetsRequested(boolean enable);
    }

    /**
     * @param directory the absolute path of the directory files are served from.
     * @param fs file system used to read the files.
     * @param insetsSupplier provides the window insets for {@code internal/insets.css}.
     * @param onInsetsRequestedListener notified when a page requests the insets.
     * @throws IllegalArgumentException if the directory is not allowed.
     */
    public RemoteFsPathHandler(
            @NonNull File directory,
            FileSystemManager fs,
            @NonNull InsetsSupplier insetsSupplier,
            OnInsetsRequestedListener onInsetsRequestedListener
    ) {
        try {
            mInsetsSupplier = insetsSupplier;
            mOnInsetsRequestedListener = onInsetsRequestedListener;
            mDirectory = new File(getCanonicalDirPath(directory));
            if (!isAllowedInternalStorageDir()) {
                throw new IllegalArgumentException("The given directory \"" + directory
                        + "\" doesn't exist under an allowed app internal storage directory");
            }
            mFs = fs;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to resolve the canonical path for the given directory: "
                            + directory.getPath(), e);
        }
    }

    private boolean isAllowedInternalStorageDir() throws IOException {
        String dir = getCanonicalDirPath(mDirectory);

        for (String forbiddenPath : FORBIDDEN_DATA_DIRS) {
            if (dir.startsWith(forbiddenPath)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Opens the requested file from the exposed directory. A missing file or a path outside the
     * directory yields a response with a {@code null} stream, which the WebView reports as a 404.
     *
     * @param path the suffix path to be handled.
     * @return {@link WebResourceResponse} for the requested file.
     */
    @Override
    @WorkerThread
    @NonNull
    public WebResourceResponse handle(@NonNull String path) {
        if ("internal/insets.css".equals(path)) {
            if (mOnInsetsRequestedListener != null) {
                mOnInsetsRequestedListener.onInsetsRequested(true);
            }
            String css = mInsetsSupplier.get().getCss();
            return new WebResourceResponse(
                "text/css",
                "utf-8",
                new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8))
            );
        }
        if ("internal/colors.css".equals(path)) {
            String css = MonetColorsProvider.INSTANCE.getColorsCss();
            return new WebResourceResponse(
                "text/css",
                "utf-8",
                new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8))
            );
        }
        try {
            File file = getCanonicalFileIfChild(mDirectory, path);
            if (file != null) {
                InputStream is = openFile(file, mFs);
                String mimeType = guessMimeType(path);
                return new WebResourceResponse(mimeType, null, is);
            } else {
                Log.e(TAG, String.format(
                        "The requested file: %s is outside the mounted directory: %s", path,
                        mDirectory));
            }
        } catch (IOException e) {
            Log.e(TAG, "Error opening the requested path: " + path, e);
        }
        return new WebResourceResponse(null, null, null);
    }

    public static String getCanonicalDirPath(@NonNull File file) throws IOException {
        String canonicalPath = file.getCanonicalPath();
        if (!canonicalPath.endsWith("/")) canonicalPath += "/";
        return canonicalPath;
    }

    public static File getCanonicalFileIfChild(@NonNull File parent, @NonNull String child)
            throws IOException {
        String parentCanonicalPath = getCanonicalDirPath(parent);
        String childCanonicalPath = new File(parent, child).getCanonicalPath();
        if (childCanonicalPath.startsWith(parentCanonicalPath)) {
            return new File(childCanonicalPath);
        }
        return null;
    }

    @NonNull
    private static InputStream handleSvgzStream(@NonNull String path,
                                                @NonNull InputStream stream) throws IOException {
        return path.endsWith(".svgz") ? new GZIPInputStream(stream) : stream;
    }

    public static InputStream openFile(@NonNull File file, @NonNull FileSystemManager fs) throws IOException {
        return handleSvgzStream(file.getPath(), fs.getFile(file.getAbsolutePath()).newInputStream());
    }

    /**
     * Use {@link MimeUtil#getMimeFromFileName} to guess MIME type or return the
     * {@link #DEFAULT_MIME_TYPE} if it can't guess.
     *
     * @param filePath path of the file to guess its MIME type.
     * @return MIME type guessed from file extension or {@link #DEFAULT_MIME_TYPE}.
     */
    @NonNull
    public static String guessMimeType(@NonNull String filePath) {
        String mimeType = MimeUtil.getMimeFromFileName(filePath);
        return mimeType == null ? DEFAULT_MIME_TYPE : mimeType;
    }
}
