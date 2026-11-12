package io.github.a13e300.ksuwebui;

import io.github.a13e300.ksuwebui.IFileChangeListener;

interface IFileWatcher {
    void start(String root, IFileChangeListener listener);
    void stop(IFileChangeListener listener);
}
