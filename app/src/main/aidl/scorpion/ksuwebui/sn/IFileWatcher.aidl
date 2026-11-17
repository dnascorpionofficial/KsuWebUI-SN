package scorpion.ksuwebui.sn;

import scorpion.ksuwebui.sn.IFileChangeListener;

interface IFileWatcher {
    void start(String root, IFileChangeListener listener);
    void stop(IFileChangeListener listener);
}
