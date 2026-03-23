public class Arc {
  private long origineId;
  private long arriveeId;
  private double distance;
  private String nomRue;

  public Arc(long origineId, long arriveeId, double distance, String nomRue) {
    this.origineId = origineId;
    this.arriveeId = arriveeId;
    this.distance = distance;
    this.nomRue = nomRue;
  }

  public long getOrigineId() {
    return origineId;
  }

  public long getArriveeId() {
    return arriveeId;
  }

  public double getDistance() {
    return distance;
  }

  public String getNomRue() {
    return nomRue;
  }
}
