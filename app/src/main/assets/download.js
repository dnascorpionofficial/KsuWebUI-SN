(function() {
    if (window.ksu_download_enabled) return;
    window.ksu_download_enabled = true;
    const blobMap = new Map();
    const orgCreate = URL.createObjectURL;
    URL.createObjectURL = (obj) => {
        const url = orgCreate(obj);
        if (obj instanceof Blob) blobMap.set(url, obj);
        return url;
    };
    const orgRevoke = URL.revokeObjectURL;
    URL.revokeObjectURL = (url) => {
        setTimeout(() => blobMap.delete(url), 10000);
        orgRevoke(url);
    };
    const handleDownload = async (a) => {
        const urlParsed = new URL(a.href, location.href);
        const url = urlParsed.href;
        const fileName = a.download || url.split("/").pop().split("?")[0] || "download.bin";
        const isInternal = urlParsed.hostname === '__DOMAIN__' || urlParsed.hostname === '__WEB_DOMAIN__';
        if (url.startsWith('blob:') || url.startsWith('data:') || isInternal) {
            const blob = (url.startsWith('blob:') && blobMap.has(url)) ? blobMap.get(url) : await (await fetch(url)).blob();
            if (blob.size > 16 * 1024 * 1024) {
                console.error("File too large, please use FileOutputStreamInterface instead.");
                return;
            }
            const reader = new FileReader();
            reader.onload = () => {
                ksu_download.save(reader.result.split(',')[1], fileName, blob.type);
            };
            reader.readAsDataURL(blob);
        } else {
            ksu_download.download(url, null, null);
        }
    };
    document.addEventListener("click", (e) => {
        const a = e.target.closest("a[download]");
        if (a) {
            e.preventDefault();
            handleDownload(a);
        }
    }, true);
    const orgClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
        this.hasAttribute("download") ? handleDownload(this) : orgClick.apply(this, arguments);
    };
})();
