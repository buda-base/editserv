package io.bdrc.edit.service;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Hex;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.txn.exceptions.GitServiceException;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class GitService implements BUDAEditService {

    int status;
    String id;
    String userId;
    String resType;
    PatchService data;

    static Repository localRepo;
    static String remoteURL;

    public GitService(String id, String resType, PatchService data) {
        this.id = "GIT_" + id;
        this.resType = resType;
        this.data = data;
        this.userId = data.getUserId();
        // log.logMsg("GIT Service " + id + " entered status ",
        // Types.getSvcStatus(Types.SVC_STATUS_READY));
    }

    /**
     * Run the service and logs execution.
     * 
     * @throws IOException
     */
    public void run() throws GitServiceException {
        System.out.println("Running GitService Service..." + id);
        this.remoteURL = EditConfig.getProperty("gitRemoteRootUrl") + resType.toLowerCase() + "s.git";
        try {
            localRepo = new FileRepositoryBuilder().setGitDir(new File(EditConfig.getProperty("gitLocalRoot") + resType + "s/.git")).setWorkTree(new File(EditConfig.getProperty("gitLocalRoot") + resType + "s/")).readEnvironment().build();
        } catch (IOException e) {
            // log.logMsg("GIT Service " + id + " entered status ",
            // Types.getSvcStatus(Types.SVC_STATUS_INIT_FAILED));
            throw new GitServiceException(e);
        }
        // log.logMsg("Git Service " + id + " entered status ",
        // Types.getSvcStatus(Types.SVC_STATUS_STARTING));
        try {
            // 1) Pull remote directory
            Git git = new Git(localRepo);
            git.pull().setProgressMonitor(new TextProgressMonitor()).setRemote("origin").call();
            System.out.println("Running GitService... remote repo was pulled.. " + id);
            /*
             * switch (type) { case Types.GIT_CREATE_SVC: // 2) check for a directory
             * corresponding for the given resource Id // if none, create one Model m =
             * data.getMod(); String dir = EditConfig.getProperty("gitLocalRoot") +
             * resType.toLowerCase() + "s/"; String sub = getDir(data.getResId()); if
             * (!hasNoDir()) { boolean exist = new
             * File(EditConfig.getProperty("gitLocalRoot") + resType.toLowerCase() + "s/" +
             * sub).exists(); if (!exist) { new File(dir + sub).mkdir(); } else { dir = dir
             * + sub + "/"; } } System.out.println("Running GitService... dir is.. " + dir +
             * " and sub =" + sub); // 3) write the new resource to the repo as ttl
             * FileOutputStream output = new FileOutputStream(dir + data.getResId() +
             * ".ttl", false); m.write(output, RDFLanguages.strLangTurtle); // 4) add and
             * commit to local git.add().addFilepattern(sub + "/").call();
             * git.commit().setMessage("Adding new resource " + data.getResId()).call(); //
             * 5) push to remote git.push().setCredentialsProvider(new
             * UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"),
             * EditConfig.getProperty("gitPassword"))).setRemote(remoteURL).call(); break;
             * case Types.GIT_DELETE_SVC: Model m_d = data.getMod(); // 2) Finding path of
             * file to be deleted String dir_d = EditConfig.getProperty("gitLocalRoot") +
             * resType.toLowerCase() + "s/"; String sub_d = getDir(data.getResId()); String
             * path = null; String rmPath = null; if (!hasNoDir()) { path = dir_d + sub_d +
             * "/" + data.getResId() + ".ttl"; rmPath = sub_d + "/" + data.getResId() +
             * ".ttl"; dir_d = dir_d + sub_d + "/"; } else { rmPath = data.getResId() +
             * ".ttl"; path = dir_d + data.getResId() + ".ttl"; }
             * System.out.println("Running GitService... dir_delete is.. " + dir_d +
             * " and sub_d =" + sub_d + " rmPath >>" + rmPath); // 3) delete the ttl file in
             * local git repo boolean delete = new File(path).delete();
             * System.out.println("Running GitService... deleting process returned : " +
             * delete); // 4) add and commit to local
             * git.rm().addFilepattern(rmPath).call();
             * git.commit().setMessage("Deleting resource " + data.getResId()).call(); // 5)
             * push to remote git.push().setCredentialsProvider(new
             * UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"),
             * EditConfig.getProperty("gitPassword"))).setRemote(remoteURL).call(); break; }
             */
            git.close();
            // log.logMsg("Git Service " + id + " entered status ",
            // Types.getSvcStatus(Types.SVC_STATUS_SUCCESS));
        } catch (GitAPIException e) {
            // log.logMsg("Git Service " + id + " entered status ",
            // Types.getSvcStatus(Types.GIT_FAILED));
            e.printStackTrace();
            throw new GitServiceException(e);
        }
    }

    public boolean rollback() throws ServiceException {
        try {
            // 1) Pull remote directory
            Git git = new Git(localRepo);
            git.pull().setProgressMonitor(new TextProgressMonitor()).setRemote("origin").call();
            /*
             * switch (type) { case Types.GIT_CREATE_SVC: // 2) delete the resource ttl file
             * thats was created before String dir = EditConfig.getProperty("gitLocalRoot")
             * + resType + "s/";
             * System.out.println("Running GitService... in create SVC with dir : " + dir);
             * String sub = getDir(data.getResId());
             * System.out.println("Running GitService... dir is.. " + dir + " and sub is: "
             * + sub); dir = dir + sub + "/"; File f = new File(dir + data.getResId() +
             * ".ttl"); boolean del = f.delete(); // 3) add and commit to local
             * git.add().addFilepattern(sub + "/").call();
             * git.commit().setMessage("Rolling back resource " + data.getResId()).call();
             * // 4) push to remote git.push().setCredentialsProvider(new
             * UsernamePasswordCredentialsProvider(EditConfig.getProperty("gitUser"),
             * EditConfig.getProperty("gitPassword"))).setRemote(remoteURL).call(); break; }
             */
        } catch (GitAPIException e) {
            e.printStackTrace();
            throw new GitServiceException(e);
        }
        return false;
    }

    private String getDir(String resId) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        int index = resId.indexOf('_');
        if (index != -1) {
            resId = resId.substring(0, index);
        }
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.reset();
        md.update(resId.getBytes(Charset.forName("UTF8")));
        String hash = new String(Hex.encodeHex(md.digest())).substring(0, 2);
        return hash;
    }

    private boolean hasNoDir() {
        return resType.equalsIgnoreCase("office") || resType.equalsIgnoreCase("corporation");
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int st) {
        status = st;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return "GIT Service";
    }

    @Override
    public String getUserId() {
        // TODO Auto-generated method stub
        return null;
    }

}
