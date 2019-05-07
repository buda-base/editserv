package io.bdrc.edit.txn;

public class QueuedPatch {

    public String slug;
    public String pragma;
    public String payload;
    public String id;
    public int status;

    public QueuedPatch(String slug, String pragma, String payload) {
        super();
        this.slug = slug;
        this.pragma = pragma;
        this.payload = payload;
        this.id = id + "_" + Long.toString(System.currentTimeMillis());
    }

    public String getSlug() {
        return slug;
    }

    public String getPragma() {
        return pragma;
    }

    public String getPayload() {
        return payload;
    }

    public String getId() {
        return id;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "QueuedPatch [slug=" + slug + ", pragma=" + pragma + ", payload=" + payload + ", id=" + id + "]";
    }

}
