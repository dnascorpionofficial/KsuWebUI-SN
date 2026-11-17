package scorpion.ksuwebui.sn;

import android.content.pm.PackageInfo;
import rikka.parcelablelist.ParcelableListSlice;

interface IKsuWebuiSnInterface {
    ParcelableListSlice<PackageInfo> getPackages(int flags);
}
