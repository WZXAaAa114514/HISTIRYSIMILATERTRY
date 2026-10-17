package shujukexianshi;

public class shujujilei {
    public String mingzi;
    public boolean hao;
    public double shuliang;

    @Override
    public String toString() {
        return String.valueOf(shuliang);
    }
    public String mingzi(){
        return mingzi;
    }
    public void set(long shuliang){
        this.shuliang=shuliang;
    }
    public shujujilei(long shuliang){
        this.shuliang=shuliang;
    }

}
