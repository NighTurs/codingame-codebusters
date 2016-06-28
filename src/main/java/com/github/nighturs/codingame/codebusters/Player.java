package com.github.nighturs.codingame.codebusters;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "NonFinalUtilityClass"})
class Player {
    static final int X_UNIT = 9000;
    static final int Y_UNIT = 16000;
    static final int FOG_UNIT = 2200;
    static final int BUSTER_SPEED = 800;
    static final int GOST_SPEED = 400;
    static final int TRAP_RANGE_INNER = 900;
    static final int TRAP_RANGE_OUTER = 1760;
    static final int STUN_RANGE = 1760;
    static final int BASE_RANGE = 1600;
    static final int STUN_COOLDOWN = 20;
    static final int TEST_END = 9999;

    static GameState gameState;
    private static Random random = new Random(1234);

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int bustersPerPlayer = in.nextInt();
        int ghostCount = in.nextInt();
        int myTeamId = in.nextInt();
        gameState = new GameState(bustersPerPlayer, ghostCount, myTeamId);

        //noinspection InfiniteLoopStatement
        while (true) {
            int entities = in.nextInt();
            if (entities == TEST_END) {
                break;
            }
            List<Buster> myBusters = new ArrayList<>();
            List<Buster> enemyBusters = new ArrayList<>();
            List<Gost> visibleGosts = new ArrayList<>();
            for (int i = 0; i < entities; i++) {
                int entityId = in.nextInt();
                int x = in.nextInt();
                int y = in.nextInt();
                int entityType = in.nextInt();
                int state = in.nextInt();
                int value = in.nextInt();
                if (entityType == -1) {
                    visibleGosts.add(Gost.create(entityId, Point.create(y, x), value, state));
                } else {
                    Integer lastTurnUsedStun = gameState.getLastTurnUsedStun().get(entityId);
                    Buster buster = Buster.create(entityId,
                            Point.create(y, x),
                            state == 1,
                            value,
                            state == 2,
                            state == 2 ? value : 0,
                            lastTurnUsedStun == null ?
                                    0 :
                                    Math.max(0, STUN_COOLDOWN - (gameState.getTurn() + 1 - lastTurnUsedStun)));
                    if (entityType == gameState.getMyTeamId()) {
                        myBusters.add(buster);
                    } else {
                        enemyBusters.add(buster);
                    }
                }
            }
            gameState.initTurn(myBusters, enemyBusters, visibleGosts);
            TurnPlan turnPlan = planTurn();
            gameState.finishTurn(turnPlan.strategies);
            System.out.print(turnPlan.formatTurn());
        }
    }

    public static TurnPlan planTurn() {
        List<Strategy> strategies = new ArrayList<>();
        strategies.addAll(SearchForGost.create(gameState.getMyBusters()));
        strategies.addAll(PrepareToCatchGost.create(gameState.getMyBusters()));
        strategies.addAll(CatchGost.create(gameState.getMyBusters()));
        strategies.addAll(BringGostToBase.create(gameState.getMyBusters()));
        strategies.addAll(ReleaseGostInBase.create(gameState.getMyBusters()));
        strategies.addAll(WaitStunExpires.create(gameState.getMyBusters()));
        strategies.addAll(StunEnemy.create(gameState.getMyBusters()));
        Map<Integer, List<Strategy>> stratsByBuster =
                strategies.stream().collect(Collectors.groupingBy(a -> a.getBuster().getId()));

        List<Strategy> goFoStrats = new ArrayList<>();
        for (List<Strategy> strats : stratsByBuster.values()) {
            final Function<Strategy, Integer> priority = (Strategy s) -> {
                if (s instanceof ReleaseGostInBase) {
                    return 0;
                } else if (s instanceof BringGostToBase) {
                    return 1;
                } else if (s instanceof CatchGost) {
                    return 2;
                } else if (s instanceof PrepareToCatchGost) {
                    return 3;
                } else if (s instanceof SearchForGost) {
                    return 4;
                } else if (s instanceof WaitStunExpires) {
                    return -100;
                } else if (s instanceof StunEnemy) {
                    return -99;
                }
                throw new RuntimeException("Unknown strategy");
            };
            goFoStrats.add(strats.stream()
                    .sorted((a, b) -> Integer.compare(priority.apply(a), priority.apply(b)))
                    .findFirst()
                    .get());
        }

        return TurnPlan.create(goFoStrats);
    }

    static int dist(Point a, Point b) {
        return (a.getX() - b.getX()) * (a.getX() - b.getX()) + (a.getY() - b.getY()) * (a.getY() - b.getY());
    }

    static int sqr(int distance) {
        return distance * distance;
    }

    static Point cap(int x, int y) {
        return Point.create(Math.min(Math.max(0, x), X_UNIT), Math.min(Math.max(0, y), Y_UNIT));
    }

    static Point moveInLine(Point a, Point b, int d, int mod) {
        int h = Math.abs(a.x - b.x);
        int w = Math.abs(a.y - b.y);
        int directionX = b.x - a.x >= 0 ? 1 : -1;
        int directionY = b.y - a.y >= 0 ? 1 : -1;
        int dd = dist(a, b);
        return cap((int) (a.x + d * h / Math.sqrt(dd) * directionX * mod),
                (int) (a.y + d * w / Math.sqrt(dd) * directionY * mod));
    }

    static Point moveToward(Point from, Point to, int d) {
        return moveInLine(from, to, d, 1);
    }

    static Point moveAway(Point from, Point to, int d) {
        return moveInLine(from, to, d, -1);
    }

    private static class WaitStunExpires implements Strategy {
        private final Buster buster;
        private final MoveBusterAction action;

        public static List<WaitStunExpires> create(List<Buster> busters) {
            List<WaitStunExpires> strategies = new ArrayList<>();
            for (Buster buster : busters) {
                if (buster.isStunned()) {
                    strategies.add(new WaitStunExpires(buster, MoveBusterAction.create(buster, buster.getPoint())));
                }
            }
            return strategies;
        }

        public WaitStunExpires(Buster buster, MoveBusterAction action) {
            this.buster = buster;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }
    }

    private static class StunEnemy implements Strategy {
        private final Buster buster;
        private final StunBusterAction action;

        public static List<StunEnemy> create(List<Buster> busters) {
            final List<StunEnemy> strategies = new ArrayList<>();

            Map<Integer, List<Buster>> busterByEnemy = new HashMap<>();

            for (Buster enemy : gameState.getEnemyBusters()) {
                for (Buster buster : busters) {
                    if (buster.isStunned() || buster.getUntilStunIsReady() > 0) {
                        continue;
                    }
                    int distance = dist(enemy.getPoint(), buster.getPoint());
                    if (distance <= sqr(STUN_RANGE)) {
                        List<Buster> whoCan = busterByEnemy.get(enemy.getId());
                        if (whoCan == null) {
                            whoCan = new ArrayList<>();
                        }
                        whoCan.add(buster);
                        busterByEnemy.put(enemy.getId(), whoCan);
                    }
                }
            }

            final Set<Integer> usedBusters = new HashSet<>();

            busterByEnemy.entrySet()
                    .stream()
                    .sorted((a, b) -> Integer.compare(a.getValue().size(), b.getValue().size()))
                    .forEach(kv -> {
                        Optional<Buster> whoCanOpt =
                                kv.getValue().stream().filter(x -> !usedBusters.contains(x.getId())).findFirst();
                        if (whoCanOpt.isPresent()) {
                            Buster whoCan = whoCanOpt.get();
                            strategies.add(new StunEnemy(whoCan,
                                    StunBusterAction.create(whoCan, gameState.getEnemyBusters()
                                            .stream()
                                            .filter(e -> e.getId() == kv.getKey())
                                            .findAny().get())));
                            usedBusters.add(whoCan.getId());
                        }
                    });

            return strategies;
        }

        public StunEnemy(Buster buster, StunBusterAction action) {
            this.buster = buster;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("StunEnemy{");
            sb.append("buster=").append(buster);
            sb.append(", action=").append(action);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class ReleaseGostInBase implements Strategy {
        private final Buster buster;
        private final ReleaseBusterAction action;

        public static List<ReleaseGostInBase> create(List<Buster> busters) {
            List<ReleaseGostInBase> strategies = new ArrayList<>();
            for (Buster buster : busters) {
                if (!buster.isCarryingGost) {
                    continue;
                }
                int distToBase = dist(buster.point, gameState.getMyBasePoint());
                if (distToBase > sqr(BASE_RANGE)) {
                    continue;
                } else {
                    strategies.add(new ReleaseGostInBase(buster, ReleaseBusterAction.create(buster)));
                }
            }
            return strategies;
        }

        public ReleaseGostInBase(Buster buster, ReleaseBusterAction action) {
            this.buster = buster;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ReleaseGostInBase{");
            sb.append("buster=").append(buster);
            sb.append(", action=").append(action);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class BringGostToBase implements Strategy {
        private final Buster buster;
        private final MoveBusterAction action;

        public static List<BringGostToBase> create(List<Buster> busters) {
            List<BringGostToBase> strategies = new ArrayList<>();
            for (Buster buster : busters) {
                if (!buster.isCarryingGost) {
                    continue;
                }
                int distToBase = dist(buster.point, gameState.getMyBasePoint());
                Point toward;
                if (distToBase > sqr(BASE_RANGE)) {
                    toward = moveToward(buster.getPoint(),
                            gameState.getMyBasePoint(),
                            (int) Math.sqrt(distToBase) - BASE_RANGE + 2);
                } else {
                    continue;
                }
                strategies.add(new BringGostToBase(buster, MoveBusterAction.create(buster, toward)));
            }
            return strategies;
        }

        private BringGostToBase(Buster buster, MoveBusterAction action) {
            this.buster = buster;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("BringGostToBase{");
            sb.append("buster=").append(buster);
            sb.append(", action=").append(action);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class CatchGost implements Strategy {
        private final Buster buster;
        private final Gost gost;
        private final BustBusterAction action;

        public static List<CatchGost> create(List<Buster> busters) {
            List<CatchGost> strategies = new ArrayList<>();
            for (Buster buster : busters) {
                if (buster.isCarryingGost) {
                    continue;
                }
                Optional<Gost> gostOpt = gameState.visibleGosts.stream()
                        .filter(g -> dist(g.getPoint(), buster.getPoint()) <= sqr(TRAP_RANGE_OUTER) &&
                                dist(g.getPoint(), buster.getPoint()) >= sqr(TRAP_RANGE_INNER)).findFirst();
                if (!gostOpt.isPresent()) {
                    continue;
                }
                strategies.add(new CatchGost(buster, gostOpt.get(), new BustBusterAction(buster, gostOpt.get())));
            }
            return strategies;
        }

        private CatchGost(Buster buster, Gost gost, BustBusterAction action) {
            this.buster = buster;
            this.gost = gost;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("CatchGost{");
            sb.append("buster=").append(buster);
            sb.append(", gost=").append(gost);
            sb.append(", action=").append(action);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class PrepareToCatchGost implements Strategy {
        private final MoveBusterAction action;
        private final Buster buster;
        private final Gost goingForGost;

        public static List<PrepareToCatchGost> create(List<Buster> busters) {
            List<PrepareToCatchGost> strategies = new ArrayList<>();

            List<Pair<Buster, Gost>> vars = new ArrayList<>();
            for (Buster buster : busters) {
                for (Gost gost : gameState.getEstimatedGosts()) {
                    vars.add(Pair.create(buster, gost));
                }
            }

            vars.sort((a, b) -> Integer.compare(dist(a.getFirst().getPoint(), a.getSecond().getPoint()),
                    dist(b.getFirst().getPoint(), b.getSecond().getPoint())));

            Set<Integer> pickedGosts = new HashSet<>();
            Set<Integer> pickedBusters = new HashSet<>();

            for (Pair<Buster, Gost> var : vars) {
                if (pickedBusters.contains(var.getFirst().getId()) || pickedGosts.contains(var.getSecond().getId())) {
                    continue;
                } else {
                    Buster buster = var.getFirst();
                    Gost gost = var.getSecond();
                    pickedBusters.add(buster.getId());
                    pickedGosts.add(gost.getId());
                    int distance = dist(buster.point, gost.escapingTo());
                    Point toward;
                    if (distance > sqr(TRAP_RANGE_OUTER)) {
                        toward = moveToward(buster.point,
                                gost.escapingTo(),
                                (int) Math.sqrt(distance) - TRAP_RANGE_OUTER + 2);
                    } else if (distance < sqr(TRAP_RANGE_INNER)) {
                        toward = moveAway(buster.point,
                                gost.escapingTo(),
                                TRAP_RANGE_INNER - (int) Math.sqrt(distance) + 2);
                    } else {
                        continue;
                    }
                    strategies.add(new PrepareToCatchGost(buster, gost, MoveBusterAction.create(buster, toward)));
                }
            }
            return strategies;
        }

        public PrepareToCatchGost(Buster buster, Gost goingForGost, MoveBusterAction action) {
            this.action = action;
            this.goingForGost = goingForGost;
            this.buster = buster;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PrepareToCatchGost{");
            sb.append("action=").append(action);
            sb.append(", buster=").append(buster);
            sb.append(", goingForGost=").append(goingForGost);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class SearchForGost implements Strategy {
        private final MoveBusterAction action;
        private final Buster buster;

        public static List<SearchForGost> create(List<Buster> busters) {
            List<SearchForGost> strategies = new ArrayList<>();
            for (Buster buster : busters) {
                List<Strategy> strats = gameState.getPrevTurnStrats();
                for (Strategy strat : strats) {
                    if (strat instanceof SearchForGost) {
                        SearchForGost prevSearch = ((SearchForGost) strat);
                        if (prevSearch.buster.getId() == buster.getId() &&
                                dist(prevSearch.action.getToPoint(), buster.getPoint()) >= sqr(BUSTER_SPEED)) {
                            strategies.add(new SearchForGost(buster,
                                    MoveBusterAction.create(buster, prevSearch.action.getToPoint())));
                        }
                    }
                }
                strategies.add(new SearchForGost(buster,
                        MoveBusterAction.create(buster, Point.create(random.nextInt(X_UNIT), random.nextInt(Y_UNIT)))));
            }
            return strategies;
        }

        private SearchForGost(Buster buster, MoveBusterAction action) {
            this.buster = buster;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("SearchForGost{");
            sb.append("action=").append(action);
            sb.append(", buster=").append(buster);
            sb.append('}');
            return sb.toString();
        }
    }

    private interface Strategy {

        BusterAction busterAction();
        Buster getBuster();
    }

    static class GameState {
        // fixed for game
        private final int bustersPerPlayer;
        private final int ghostCount;
        private final int myTeamId;
        private final Point myBasePoint;
        // fixed for turn
        private List<Buster> myBusters;
        private List<Buster> enemyBusters;
        private List<Gost> visibleGosts;
        // memory
        private int turn = -1;
        private List<Strategy> prevTurnStrats = Collections.emptyList();
        private Map<Integer, Integer> lastTurnUsedStun = new HashMap<>();
        private Map<Integer, Gost> estimatedGosts = new HashMap<>();

        public GameState(int bustersPerPlayer, int ghostCount, int myTeamId) {
            this.bustersPerPlayer = bustersPerPlayer;
            this.ghostCount = ghostCount;
            this.myTeamId = myTeamId;
            this.myBasePoint = myTeamId == 0 ? Point.create(0, 0) : Point.create(X_UNIT, Y_UNIT);
        }

        @SuppressWarnings("ParameterHidesMemberVariable")
        public void initTurn(List<Buster> myBusters, List<Buster> enemyBusters, List<Gost> visibleGosts) {
            this.turn++;
            this.myBusters = myBusters;
            this.enemyBusters = enemyBusters;
            this.visibleGosts = visibleGosts;
            removeFalseGostEstimations();
        }

        public void finishTurn(List<Strategy> strategies) {
            this.prevTurnStrats = strategies;
            for (Strategy strategy : strategies) {
                if (strategy instanceof StunEnemy) {
                    lastTurnUsedStun.put(strategy.getBuster().getId(), getTurn());
                }
            }
            estimateGostPositions();
        }

        private void removeFalseGostEstimations() {
            Set<Integer> visibleGostsIds = visibleGosts.stream().map(Gost::getId).collect(Collectors.toSet());
            List<Gost> toRemove = new ArrayList<>();
            for (Gost gost : estimatedGosts.values()) {
                if (visibleGostsIds.contains(gost.getId())) {
                    continue;
                }
                for (Buster buster : myBusters) {
                    int distance = dist(buster.getPoint(), gost.getPoint());
                    if (distance <= sqr(FOG_UNIT)) {
                        toRemove.add(gost);
                    }
                }
            }
            for (Gost gostToRemove : toRemove) {
                estimatedGosts.remove(gostToRemove.getId());
            }
        }

        private void estimateGostPositions() {
            for (Gost gost : visibleGosts) {
                Gost futureTurnsGost =
                        Gost.create(gost.getId(), gost.escapingTo(), gost.getTrapAttempts(), gost.getStamina());
                estimatedGosts.put(futureTurnsGost.getId(), futureTurnsGost);
            }
        }

        public int getBustersPerPlayer() {
            return bustersPerPlayer;
        }

        public int getGhostCount() {
            return ghostCount;
        }

        public int getMyTeamId() {
            return myTeamId;
        }

        public List<Buster> getMyBusters() {
            return myBusters;
        }

        public List<Buster> getEnemyBusters() {
            return enemyBusters;
        }

        public List<Gost> getVisibleGosts() {
            return visibleGosts;
        }

        public List<Strategy> getPrevTurnStrats() {
            return prevTurnStrats;
        }

        public Point getMyBasePoint() {
            return myBasePoint;
        }

        public int getTurn() {
            return turn;
        }

        public Map<Integer, Integer> getLastTurnUsedStun() {
            return lastTurnUsedStun;
        }

        public Collection<Gost> getEstimatedGosts() {
            return estimatedGosts.values();
        }
    }

    private static final class TurnPlan {
        private final List<Strategy> strategies;

        private static TurnPlan create(List<Strategy> strategies) {
            return new TurnPlan(strategies);
        }

        private TurnPlan(List<Strategy> strategies) {
            this.strategies = strategies;
        }

        public String formatTurn() {
            StringBuilder sb = new StringBuilder();
            strategies.forEach(x -> System.err.println(x));
            strategies.stream()
                    .map(Strategy::busterAction)
                    .sorted((a, b) -> Integer.compare(a.getBuster().getId(), b.getBuster().getId()))
                    .forEach(x -> sb.append(x.formatLine()).append("\n"));
            return sb.toString();
        }
    }

    private static final class StunBusterAction implements BusterAction {
        private final Buster buster;
        private final Buster enemyBuster;

        public static StunBusterAction create(Buster buster, Buster enemyBuster) {
            return new StunBusterAction(buster, enemyBuster);
        }

        public StunBusterAction(Buster buster, Buster enemyBuster) {
            this.buster = buster;
            this.enemyBuster = enemyBuster;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String formatLine() {
            return String.format("STUN %s", enemyBuster.getId());
        }
    }

    private static final class ReleaseBusterAction implements BusterAction {

        private final Buster buster;

        public static ReleaseBusterAction create(Buster buster) {
            return new ReleaseBusterAction(buster);
        }

        private ReleaseBusterAction(Buster buster) {
            this.buster = buster;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String formatLine() {
            return "RELEASE";
        }
    }

    private static final class BustBusterAction implements BusterAction {
        private final Buster buster;
        private final Gost gost;

        public static BustBusterAction create(Buster buster, Gost gost) {
            return new BustBusterAction(buster, gost);
        }

        private BustBusterAction(Buster buster, Gost gost) {
            this.buster = buster;
            this.gost = gost;
        }

        @Override
        public String formatLine() {
            return String.format("BUST %s", gost.getId());
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        public Gost getGost() {
            return gost;
        }
    }

    private static final class MoveBusterAction implements BusterAction {

        private final Buster buster;
        private final Point toPoint;

        public static MoveBusterAction create(Buster buster, Point toPoint) {
            return new MoveBusterAction(buster, toPoint);
        }

        private MoveBusterAction(Buster buster, Point toPoint) {
            this.buster = buster;
            this.toPoint = toPoint;
        }

        @Override
        public String formatLine() {
            return String.format("MOVE %s %s", toPoint.getY(), toPoint.getX());
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        public Point getToPoint() {
            return toPoint;
        }
    }

    private interface BusterAction {
        Buster getBuster();
        String formatLine();
    }

    static final class Buster {
        private final int id;
        private final Point point;
        private final boolean isCarryingGost;
        private final int carriedGostId;
        private final boolean isStunned;
        private final int untilStunExpires;
        private final int untilStunIsReady;

        public static Buster create(int id,
                                    Point point,
                                    boolean isCarryingGost,
                                    int carriedGostId,
                                    boolean isStunned,
                                    int untilStunExpires,
                                    int untilStunIsReady) {
            return new Buster(id, point, isCarryingGost, carriedGostId, isStunned, untilStunExpires, untilStunIsReady);
        }

        public Buster(int id,
                      Point point,
                      boolean isCarryingGost,
                      int carriedGostId,
                      boolean isStunned,
                      int untilStunExpires,
                      int untilStunIsReady) {
            this.id = id;
            this.point = point;
            this.isCarryingGost = isCarryingGost;
            this.carriedGostId = carriedGostId;
            this.isStunned = isStunned;
            this.untilStunExpires = untilStunExpires;
            this.untilStunIsReady = untilStunIsReady;
        }

        public int getId() {
            return id;
        }

        public Point getPoint() {
            return point;
        }

        public boolean isCarryingGost() {
            return isCarryingGost;
        }

        public int getCarriedGostId() {
            return carriedGostId;
        }

        public boolean isStunned() {
            return isStunned;
        }

        public int getUntilStunExpires() {
            return untilStunExpires;
        }

        public int getUntilStunIsReady() {
            return untilStunIsReady;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Buster{");
            sb.append("id=").append(id);
            sb.append(", point=").append(point);
            sb.append(", isCarryingGost=").append(isCarryingGost);
            sb.append(", carriedGostId=").append(carriedGostId);
            sb.append(", isStunned=").append(isStunned);
            sb.append(", untilStunExpires=").append(untilStunExpires);
            sb.append(", untilStunIsReady=").append(untilStunIsReady);
            sb.append('}');
            return sb.toString();
        }
    }

    static final class Gost {
        private final int id;
        private final Point point;
        private final int trapAttempts;
        private final int stamina;

        static Gost create(int id, Point point, int trapAttempts, int stamina) {

            return new Gost(id, point, trapAttempts, stamina);
        }

        public Gost(int id, Point point, int trapAttempts, int stamina) {
            this.id = id;
            this.point = point;
            this.trapAttempts = trapAttempts;
            this.stamina = stamina;
        }

        public Point escapingTo() {
            int x = 0, y = 0;
            int count = 0;
            for (Buster buster : gameState.enemyBusters) {
                if (dist(buster.getPoint(), point) <= sqr(FOG_UNIT)) {
                    x += buster.point.getX();
                    y += buster.point.getY();
                    count++;
                }
            }
            for (Buster buster : gameState.myBusters) {
                if (dist(buster.getPoint(), point) <= sqr(FOG_UNIT)) {
                    x += buster.point.getX();
                    y += buster.point.getY();
                    count++;
                }
            }
            if (count == 0) {
                return point;
            }

            int meanX = x / count;
            int meanY = y / count;

            return moveAway(point, Point.create(meanX, meanY), GOST_SPEED);
        }

        public int getId() {
            return id;
        }

        public Point getPoint() {
            return point;
        }

        public int getTrapAttempts() {
            return trapAttempts;
        }

        public int getStamina() {
            return stamina;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Gost{");
            sb.append("id=").append(id);
            sb.append(", point=").append(point);
            sb.append(", trapAttempts=").append(trapAttempts);
            sb.append(", escapingTo=").append(escapingTo());
            sb.append('}');
            return sb.toString();
        }
    }

    interface PointBase {
        int getX();
        int getY();
    }

    static final class Point implements PointBase {
        final int x, y;

        public static Point create(int x, int y) {
            return new Point(x, y);
        }

        private Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Point point = (Point) o;
            return x == point.x && y == point.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Point{");
            sb.append("x=").append(x);
            sb.append(", y=").append(y);
            sb.append('}');
            return sb.toString();
        }
    }

    static final class PointMutable implements PointBase {

        int x, y;

        public static PointMutable create(int x, int y) {
            return new PointMutable(x, y);
        }

        private PointMutable(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int getX() {
            return 0;
        }

        @Override
        public int getY() {
            return 0;
        }

        public void setX(int x) {
            this.x = x;
        }

        public void setY(int y) {
            this.y = y;
        }
    }

    static final class Pair<K, V> {
        private final K first;
        private final V second;
        public static <K, V> Pair<K, V> create(K first, V second) {
            return new Pair<>(first, second);
        }

        private Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }

        public K getFirst() {
            return first;
        }

        public V getSecond() {
            return second;
        }
    }
}