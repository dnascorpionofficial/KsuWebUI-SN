(function(paths) {
    var links = Array.prototype.slice.call(document.querySelectorAll('link[rel~="stylesheet"][href]'));
    var decode = function(value) {
        try {
            return decodeURIComponent(value);
        } catch (e) {
            return value;
        }
    };
    var all = true;
    paths.forEach(function(path) {
        var target = '/' + decode(path);
        var matches = links.filter(function(link) {
            var url = new URL(link.href, document.baseURI);
            return url.host === location.host && decode(url.pathname) === target;
        });
        if (!matches.length) {
            all = false;
            return;
        }
        matches.forEach(function(link) {
            var url = new URL(link.href, document.baseURI);
            url.searchParams.set('ksulr', Date.now());
            var fresh = link.cloneNode();
            fresh.href = url.href;
            var drop = function() {
                link.remove();
            };
            fresh.addEventListener('load', drop);
            fresh.addEventListener('error', function() {
                fresh.remove();
            });
            link.after(fresh);
        });
    });
    return all;
})(PATHS_JSON);
