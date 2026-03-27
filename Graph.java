
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

    private Map<Long, Localisation> noeuds;
    private Map<Long, List<Arc>> adjacents;

    public Graph(String localisations, String roads)  {
        this.noeuds = new HashMap<>();
        this.adjacents = new HashMap<>();

        loadLocalisation(localisations);
        loadRoads(roads);
    }

    private void loadLocalisation(String localisations){
        try (BufferedReader br = new BufferedReader(new FileReader(new File(localisations)))) {
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
        try (BufferedReader br = new BufferedReader(new FileReader(new File(roads)))){
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
        Queue<Long> fileBFS = new LinkedList<>();

        Set<Long> inondes = new LinkedHashSet<>();

        for (long id: idsOrigin){
            fileBFS.add(id);
            inondes.add(id);
        }

        while (!fileBFS.isEmpty()){
            long currentId = fileBFS.poll();
            Localisation currentNoeud = noeuds.get(currentId);
            List<Arc> roadNoeud = adjacents.getOrDefault(currentId, new ArrayList<>());

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

        while (!fileBFS.isEmpty()){
            long currentId = fileBFS.poll();
            List<Arc> roadNoeud = adjacents.getOrDefault(currentId, new ArrayList<>());

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
        if (!destinationAtteinte) {
            throw new RuntimeException("Pas de chemin de " + idOrigin + " à " + idDestination + " évitant la zone inondée.");
        }

        return getLocalisations(idOrigin, idDestination, parents);
    }

    public Map<Localisation,Double> determinerChronologieDeLaCrue(long[] idsOrigin, double vWaterInit,double k) {
        // 2. File de priorité (Dijkstra) qui trie automatiquement par temps d'arrivée croissant
        PriorityQueue<NoeudEau> filePriorite = new PriorityQueue<>(Comparator.comparingDouble(n -> n.tempsArrivee));

        // 3. Le résultat final : LinkedHashMap est OBLIGATOIRE ici pour respecter l'ordre croissant demandé
        Map<Localisation, Double> tFlood = new LinkedHashMap<>();

        // Étiquettes provisoires pour éviter de surcharger la file de priorité
        Map<Long, Double> meilleursTemps = new HashMap<>();

        // 4. Initialisation des points de départ
        for (long id : idsOrigin) {
            filePriorite.add(new NoeudEau(id, 0.0, vWaterInit));
            meilleursTemps.put(id, 0.0);
        }

        // 5. Boucle principale de Dijkstra
        while (!filePriorite.isEmpty()) {
            NoeudEau courant = filePriorite.poll();
            long currentId = courant.id;
            double currentTime = courant.tempsArrivee;
            double currentSpeed = courant.vitesseEau;

            Localisation currentLoc = noeuds.get(currentId);

            // Si le noeud a déjà son étiquette définitive, on l'ignore (Dijkstra de base)
            if (tFlood.containsKey(currentLoc)) {
                continue;
            }

            // On valide définitivement ce noeud à ce temps précis.
            // Grâce à la file de priorité, on est certain que l'insertion se fait en ordre croissant !
            tFlood.put(currentLoc, currentTime);

            // On explore les rues adjacentes
            List<Arc> routesSortantes = adjacents.getOrDefault(currentId, new ArrayList<>());
            for (Arc route : routesSortantes) {
                long voisinId = route.getArriveeId();
                Localisation voisin = noeuds.get(voisinId);

                // Si le voisin est déjà inondé définitivement, on passe
                if (tFlood.containsKey(voisin)) {
                    continue;
                }

                double distance = route.getDistance();

                // --- CALCULS PHYSIQUES ---

                // Pente S = (Alt(A) - Alt(B)) / Distance
                double penteS = (currentLoc.getAltitude() - voisin.getAltitude()) / distance;

                // Nouvelle vitesse = V_actuelle + (k * S)
                double newSpeed = currentSpeed + (k * penteS);

                // CONDITION DE PROPAGATION : L'eau s'arrête si la vitesse <= 0
                if (newSpeed > 0) {

                    // Temps de parcours = Distance / V_actuelle
                    double tempsTrajet = distance / newSpeed;
                    double arrivalTime = currentTime + tempsTrajet;

                    // Si on a trouvé un moyen plus rapide d'inonder ce voisin
                    if (arrivalTime < meilleursTemps.getOrDefault(voisinId, Double.MAX_VALUE)) {

                        meilleursTemps.put(voisinId, arrivalTime);

                        // On propage l'eau avec son nouveau temps et sa NOUVELLE vitesse !
                        filePriorite.add(new NoeudEau(voisinId, arrivalTime, newSpeed));
                    }
                }
            }
        }

        return tFlood;
    }

    public Deque<Localisation> trouverCheminDEvacuationLePlusCourt(long idOrigin, long idEvacuation, double vVehicule, Map<Localisation,Double> tFlood) {
        // 1. La PriorityQueue : Elle trie automatiquement par "tempsArrivee" croissant
        PriorityQueue<NoeudTemps> filePriorite = new PriorityQueue<>(Comparator.comparingDouble(nt -> nt.tempsArrivee));

        // 2. Les étiquettes provisoires : Mémorise le meilleur temps pour atteindre chaque noeud
        Map<Long, Double> meilleursTemps = new HashMap<>();

        // 3. Les étiquettes définitives (Noeuds déjà validés)
        Set<Long> visites = new HashSet<>();

        // 4. Pour reconstruire le chemin à la fin
        Map<Long, Long> parents = new HashMap<>();

        // Initialisation à t = 0
        filePriorite.add(new NoeudTemps(idOrigin, 0.0));
        meilleursTemps.put(idOrigin, 0.0);

        boolean destinationAtteinte = false;

        while (!filePriorite.isEmpty()) {
            // On prend le noeud avec l'étiquette de temps la plus petite
            NoeudTemps courant = filePriorite.poll();
            long currentId = courant.id;
            double currentTime = courant.tempsArrivee;

            // Si on l'a déjà validé (étiquette définitive), on l'ignore
            if (visites.contains(currentId)) continue;

            // On le marque comme définitif
            visites.add(currentId);

            // Condition d'arrêt : on a atteint la destination de manière optimale
            if (currentId == idEvacuation) {
                destinationAtteinte = true;
                break;
            }

            // On explore ses voisins
            List<Arc> routesSortantes = adjacents.getOrDefault(currentId, new ArrayList<>());
            for (Arc route : routesSortantes) {
                long voisinId = route.getArriveeId();
                Localisation noeudVoisin = noeuds.get(voisinId);

                // Calcul du nouveau temps : t = t_actuel + (distance / vitesse)
                double tempsTrajetArc = route.getDistance() / vVehicule;
                double tempsArriveeVoisin = currentTime + tempsTrajetArc;

                // --- VÉRIFICATION DE LA CONTRAINTE DYNAMIQUE (L'INONDATION) ---
                // On cherche l'heure de l'inondation pour ce voisin (si absente, on met l'infini)
                Double floodTimeObj = tFlood.get(noeudVoisin);
                double tempsInondation = (floodTimeObj != null) ? floodTimeObj : Double.MAX_VALUE;

                // Le véhicule ne doit pas arriver APRÈS ni AU MOMENT où c'est inondé.
                // Donc le temps d'arrivée doit être STRICTEMENT INFÉRIEUR au temps d'inondation.
                if (tempsArriveeVoisin < tempsInondation) {

                    // Si on a trouvé un moyen PLUS RAPIDE d'y arriver
                    if (tempsArriveeVoisin < meilleursTemps.getOrDefault(voisinId, Double.MAX_VALUE)) {

                        // On met à jour l'étiquette provisoire
                        meilleursTemps.put(voisinId, tempsArriveeVoisin);
                        parents.put(voisinId, currentId); // Le petit poucet !

                        // On l'ajoute à la file de priorité pour la suite
                        filePriorite.add(new NoeudTemps(voisinId, tempsArriveeVoisin));
                    }
                }
            }
        }
        if (!destinationAtteinte) {
            throw new RuntimeException("Pas de chemin de " + idOrigin + " à " + idEvacuation + " évitant la zone inondée.");
        }
        return getLocalisations(idOrigin, idEvacuation, parents);
    }

    private Deque<Localisation> getLocalisations(long idOrigin, long idDestination, Map<Long, Long> parents) {
        Deque<Localisation> cheminFinal = new LinkedList<>();

        long etapeId = idDestination;
        while (etapeId != idOrigin) {
            cheminFinal.addFirst(noeuds.get(etapeId));
            etapeId = parents.get(etapeId);
        }
        cheminFinal.addFirst(noeuds.get(idOrigin));

        return cheminFinal;
    }

    private static class NoeudEau {
        long id;
        double tempsArrivee;
        double vitesseEau;

        public NoeudEau(long id, double tempsArrivee, double vitesseEau) {
            this.id = id;
            this.tempsArrivee = tempsArrivee;
            this.vitesseEau = vitesseEau;
        }
    }

    private static class NoeudTemps {
        long id;
        double tempsArrivee;

        public NoeudTemps(long id, double tempsArrivee) {
            this.id = id;
            this.tempsArrivee = tempsArrivee;
        }
    }


}
