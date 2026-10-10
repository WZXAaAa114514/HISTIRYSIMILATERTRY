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
    public void set(int shuliang){
        this.shuliang=shuliang;
    }
    public shujujilei(int shuliang){
        this.shuliang=shuliang;
    }

}
