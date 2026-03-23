public class Localisation {
  private long id;
  private double latitude;
  private double longitude;
  private String nom;
  private double altitude;

  public Localisation(long id, double latitude, double longitude, String nom, double altitude) {
    this.id = id;
    this.latitude = latitude;
    this.longitude = longitude;
    this.nom = nom;
    this.altitude = altitude;
  }

  public long getId() {
    return id;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public String getNom() {
    return nom;
  }

  public double getAltitude() {
    return altitude;
  }
}
