
package tv.avfun.util.download;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.util.Log;


import tv.avfun.entity.VideoPart;
import tv.avfun.entity.VideoSegment;
import tv.avfun.util.ArrayUtil;
import tv.avfun.util.download.exception.IllegalEntryException;
/**
 * Download jobs provider.
 * @author Yrom
 *
 */
public class DownloadProvider {
    private static final String TAG = "DownloadProvider";
    private List<DownloadJob> mQueuedJobs;
    private List<DownloadJob> mCompletedJobs;
    private DownloadManager mDownloadManager;
    private DownloadDB mDb;
    private List<DownloadJob> allJobs;
    
    public DownloadProvider(Context context, DownloadManager manager) {
        this.mDownloadManager = manager;
        mQueuedJobs = new ArrayList<DownloadJob>();
        mCompletedJobs = new ArrayList<DownloadJob>();
        mDb = new DownloadDBImpl(context);
    }

    public void loadOldDownloads() {
        List<DownloadJob> oldDownloads = mDb.getAllDownloads();
        for(DownloadJob j : oldDownloads){
            if(!j.isRunning()){
                mCompletedJobs.add(j); // complete
            }else{
                // start failed job
                // mDownloadManager.download(j.getEntry());
                // 应由用户启动
                j.getEntry().part.isDownloading = true;
                mQueuedJobs.add(j);
            }
        }
        mDownloadManager.notifyAllObservers();
    }

    public List<DownloadJob> getAllDownloads() {
        if(allJobs!=null)
            allJobs.clear();
        else 
            allJobs = new ArrayList<DownloadJob>();
        allJobs.addAll(mCompletedJobs);
        allJobs.addAll(mQueuedJobs);
        return allJobs;
    }

    public List<DownloadJob> getCompletedDownloads() {
        return mCompletedJobs;
    }

    public List<DownloadJob> getQueuedDownloads() {
        return mQueuedJobs;
    }
    
    public DownloadJob getQueueJobByVid(String vid){
        for(DownloadJob j : mQueuedJobs){
            if(j.getEntry().part.vid.equals(vid)) 
                return j;
        }
        return null;
    }

    public List<VideoPart> getVideoParts(String aid){
        List<VideoPart> parts = null;
        for(DownloadJob j : getAllDownloads()){
            if(j.getEntry().aid.equals(aid)){
                if(parts == null)
                    parts = new ArrayList<VideoPart>();
                parts.add(j.getEntry().part);
            }
        }
        return parts;
    }
    /**
     * mark job completed
     */
    public void complete(int status, DownloadJob job) {
        mQueuedJobs.remove(job);
        mCompletedJobs.add(job);
//        由task自己更新status
//        for(VideoSegment s :job.getEntry().part.segments){
//            setStatus(job.getEntry().part.vid, s.num, status);
//        }
        mDownloadManager.notifyAllObservers();
    }
    public void resume(DownloadJob job){
        // TODO mark job resuming
    }
    public void update(String vid, int num, ContentValues values){
        mDb.updateDownload(vid,num,values);
    }
    public void setStatus(String vid, int num, int status){
        ContentValues values = new ContentValues();
        values.put(DownloadDB.COLUMN_STATUS, status);
        update(vid, num, values);
    }
    public void setEtag(String vid, int num, String etag){
        ContentValues values = new ContentValues();
        values.put(DownloadDB.COLUMN_ETAG, etag);
        update(vid, num, values);
    }
    public boolean enqueue(DownloadJob job) {
        if(contains(mCompletedJobs,job.getEntry().part)
                ||contains(mQueuedJobs,job.getEntry().part))
                return false;
        
        try {
            job.getEntry().part.isDownloading = true;
            mDb.addDownload(job.getEntry());
            mQueuedJobs.add(job);
            mDownloadManager.notifyAllObservers();
            return true;
        } catch (IllegalEntryException e) {
            Log.e(TAG, "fail to enqueue",e);
            return false;
        }
    }
    /**
     * Cancel download job and remove it from db
     * @param job
     */
    public void removeDownload(DownloadJob job) {
        if(job.isRunning()){ 
            job.cancel();
            mQueuedJobs.remove(job);
        }else{
            mCompletedJobs.remove(job);
        }
        mDb.remove(job);
        mDownloadManager.notifyAllObservers();
    }
    private boolean contains(List<DownloadJob> jobs, VideoPart part){
        for(DownloadJob j : jobs){
            if(j.getEntry().part.equals(part)) 
                return true;
        }
        return false;
        
    }
    /**
     * Is video part available?
     */
    public boolean isPartDownloaded(VideoPart part){
        return contains(mCompletedJobs,part);
    }
}
