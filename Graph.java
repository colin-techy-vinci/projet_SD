
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

public class Graph {

	//ATTRIBUT ?
	//TODO

    private Map<Long, Localisation> noeuds;
    private Map<Long, List<Arc>> adjacents;

    public Graph(String localisations, String roads)  {
        //TODO
        this.noeuds = new HashMap<>();
        this.adjacents = new HashMap<>();

        loadLocalisation(localisations);
        loadRoads(roads);
    }

    private void loadLocalisation(String localisations){
        try {
            File file = new File(localisations);

            FileReader fr = new FileReader(file);

            BufferedReader br = new BufferedReader(fr);
            String line;

            //on fait appel à un premier readLine car pour skip en tête de fichier csv
            br.readLine();

            while ((line = br.readLine()) != null){
                String[] data = line.split(",");
                if (data.length >= 5){
                    long id = Long.parseLong(data[0].trim());
                    String nom = data[1].trim();
                    double lat = Double.parseDouble(data[2].trim());
                    double lon = Double.parseDouble(data[3].trim());
                    double alt = Double.parseDouble(data[4].trim());

                    Localisation loc = new Localisation(id, lat, lon, nom, alt);

                    noeuds.put(id, loc);
                    adjacents.put(id, new ArrayList<>());
                }
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    private void loadRoads(String roads){
        try {
            File file = new File(roads);

            FileReader fr = new FileReader(file);

            BufferedReader br = new BufferedReader(fr);
            String line;

            //on fait appel à un premier readLine car pour skip en tête de fichier csv
            br.readLine();

            while ((line = br.readLine()) != null){
                String[] data = line.split(",");
                if (data.length >= 4){
                    long source = Long.parseLong(data[0].trim());
                    long target = Long.parseLong(data[1].trim());
                    double dist = Double.parseDouble(data[2].trim());
                    String streetName = data[3].trim();
                    Arc arc = new Arc(source, target, dist, streetName);

                    adjacents.get(source).add(arc);
                }
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public Localisation[] determinerZoneInondee(long[] idsOrigin,double epsilon)   {
        //TODO
        Queue<Long> fileBFS = new LinkedList<>();

        Set<Long> inondes = new LinkedHashSet<>();

        for (long id: idsOrigin){
            fileBFS.add(id);
            inondes.add(id);
        }

        while (!fileBFS.isEmpty()){
            long currentId = fileBFS.poll();
            Localisation currentNoeud = noeuds.get(currentId);
            List<Arc> roadNoeud = adjacents.get(currentId);

            if (roadNoeud.isEmpty())
                continue;

            for (Arc road: roadNoeud){
                long voisinId = road.getArriveeId();
                Localisation voisin = noeuds.get(voisinId);

                if (!inondes.contains(voisinId)) {
                    if (voisin.getAltitude() <= currentNoeud.getAltitude() + epsilon){
                        inondes.add(voisinId);
                        fileBFS.add(voisinId);
                    }
                }
            }
        }

        Localisation[] ret = new Localisation[inondes.size()];
        int i = 0;
        for (long idInonde : inondes) {
            ret[i] = noeuds.get(idInonde);
            i++;
        }

        return ret;
    }

    public Deque<Localisation> trouverCheminLePlusCourtPourContournerLaZoneInondee(long idOrigin, long idDestination, Localisation[] floodedZone) {
		//TODO
        Queue<Long> fileBFS = new LinkedList<>();

        // ajout zone inondée
        Set<Long> inaccessibles = new HashSet<>();
        for (Localisation loc : floodedZone) {
            inaccessibles.add(loc.getId());
        }

        Map<Long, Long> parents = new HashMap<>();

        fileBFS.add(idOrigin);
        inaccessibles.add(idOrigin);

        boolean destinationAtteinte = false;

        // [
        //  origin -> a -> b -> c -> d
        //  origin -> b -> d
        // ]

        while (!fileBFS.isEmpty()){
            long currentId = fileBFS.poll();
            List<Arc> roadNoeud = adjacents.get(currentId);

            if (currentId == idDestination){
                destinationAtteinte = true;
                break;
            }
            for (Arc road: roadNoeud){
                long voisinId = road.getArriveeId();

                if (!inaccessibles.contains(voisinId)){
                    fileBFS.add(voisinId);

                    inaccessibles.add(voisinId);

                    parents.put(voisinId, currentId);
                }
            }
        }

        Deque<Localisation> cheminFinal = new LinkedList<>();
        if (destinationAtteinte){
            long etapeId = idDestination;

            while (etapeId != idOrigin){
                cheminFinal.addFirst(noeuds.get(etapeId));
                etapeId = parents.get(etapeId);
            }
            cheminFinal.addFirst(noeuds.get(idOrigin));
        }
        return cheminFinal;
    }

    public Map<Localisation, Double> determinerChronologieDeLaCrue(long[] idsOrigin, double vWaterInit, double k) {

        // Carnet final : heure d'inondation de chaque noeud
        // LinkedHashMap garde l'ordre d'insertion = ordre croissant de temps
        Map<Localisation, Double> tFlood = new LinkedHashMap<>();

        // File de priorité : [nodeId, tFlood, vWater]
        // Trie automatiquement par tFlood croissant (le plus urgent en premier)
        PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(e -> e[1]));

        // DÉPART : tous les points d'origine sont inondés à t=0
        for (long id : idsOrigin) {
            Localisation loc = noeuds.get(id);
            if (loc != null) {
                tFlood.put(loc, 0.0);
                pq.offer(new double[]{id, 0.0, vWaterInit});
            }
        }

        while (!pq.isEmpty()) {

            double[] current = pq.poll();
            long    currentId = (long) current[0];
            double  tCourant  = current[1];
            double  vCourant  = current[2];

            Localisation currentLoc = noeuds.get(currentId);

            // OPTIMISATION CLÉ : si ce noeud a déjà été finalisé avec un meilleur
            // temps, on ignore cette entrée (elle est périmée)
            if (tFlood.containsKey(currentLoc) && tFlood.get(currentLoc) < tCourant) continue;

            // On explore tous les voisins du noeud courant
            List<Arc> arcs = adjacents.get(currentId);
            if (arcs == null) continue;

            for (Arc arc : arcs) {

                Localisation voisin = noeuds.get(arc.getArriveeId());
                if (voisin == null) continue;

                // PHYSIQUE : la pente détermine si l'eau accélère ou ralentit
                double pente   = (currentLoc.getAltitude() - voisin.getAltitude()) / arc.getDistance();
                double vVoisin = vCourant + k * pente;

                // L'eau s'arrête si sa vitesse devient nulle ou négative
                if (vVoisin <= 0) continue;

                // Temps pour traverser cet arc + heure d'arrivée au voisin
                double tVoisin = tCourant + arc.getDistance() / vVoisin;

                // RELAXATION : on met à jour seulement si on a trouvé un chemin plus rapide
                if (!tFlood.containsKey(voisin) || tVoisin < tFlood.get(voisin)) {
                    tFlood.put(voisin, tVoisin);
                    pq.offer(new double[]{arc.getArriveeId(), tVoisin, vVoisin});
                }
            }
        }

        return tFlood;
    }

    public Deque<Localisation> trouverCheminDEvacuationLePlusCourt(long idOrigin, long idEvacuation, double vVehicule, Map<Localisation,Double> tFlood) {
        //TODO
		return null ;
    }


}
