package sqdance.g1;

import sqdance.sim.Point;

import java.io.*;
import java.util.*;

public class ConveyorPlayer implements sqdance.sim.Player {

    // globals
    private Point[][] grid;
    private int[][] pairGrid; // grid of pairs, 20x40, 0 if NOT a conveyor pair, otherwise int is number of rows in conveyor
    private int pairGridCols = 20;
    private int pairGridRows = 40;
    private Point[][] conveyor_rows;
    private int gridCols = 0; // number of columns (must be even number...)
    private int gridRows = 0; // number of pairs per column
    private Point[] snake; // size = num dancers in snake - 1 (stationary dancer isn't in snake)
    private int snakeMovingLen; // number of dancers that move in the snake (size of snake)
    private List<Integer> snakeDancers; // holds dancer ids of those in the snake
    private int stationaryDancer; // dancer id of the stationary one
    private int mode = 0; // 0: dance, 1: evaluate and make moves
    private Point[] destinations;

    // constants
    private final double GRID_GAP = 0.50000001; // distance between grid points
    private final double DANCE_EPSILON = 0.000000001; // distance to move so that pair will dance
    private final double GRID_OFFSET_X = 0.00000000001; // offset of entire grid from 0,0
    private final double GRID_OFFSET_Y = 0.00000000001;
    private final double CONVEYOR_GAP = 0.1000000001; // distance between points within a conveyor
    private final int SCALING_SNAKE_THRESHOLD = 2400; // for d > this and use scaling snake instead of createSnake
    private final boolean LSD_OPTIMIZATION = false; // optimize for lowest scoring dancer
    private final int DANCE_TURNS = 10; // number of turns to dance before move
    
    // E[i][j]: the remaining enjoyment player j can give player i
    // -1 if the value is unknown (everything unknown upon initialization)
    private int[][] E = null;

    // random generator
    private Random random = null;

    // simulation parameters
    private int d = -1;
    private double room_side = -1;

    // init function called once with simulation parameters before anything else is called
    public void init(int d, int room_side) {
        this.d = d;
        this.room_side = (double) room_side;


        
        // create the grid
        double side = room_side / GRID_GAP;
        gridCols = (int) side + 1; 
        gridRows = (int) side + 1;

        grid = new Point[gridCols][gridRows]; // this should be 40x40
        for (int i = 0; i < gridCols; i++) {
            for (int j = 0; j < gridRows; j++) {
                double gridX = GRID_OFFSET_X + i * GRID_GAP;
                double gridY = GRID_OFFSET_Y + j * GRID_GAP;
                if ((i % 2) == 1) {
                    gridX -= DANCE_EPSILON;
                }
                grid[i][j] = new Point(gridX, gridY);
            }
        }

        // create grid of pairs
        pairGridCols = gridCols / 2;
        pairGridRows = gridRows;
        pairGrid = new int[pairGridCols][pairGridRows];
        for (int i = 0; i < pairGridCols; i++) {
            for (int j = 0; j < pairGridRows; j++) {
                pairGrid[i][j] = 0;
            }
        }
        
        snakeDancers = new ArrayList<Integer>();
        destinations = new Point[d];
        // create snake positions
        if (d > SCALING_SNAKE_THRESHOLD) {
            snake = createScalingSnake(d);
        }
        else {
            snake = createSnake(d);
        }
    }

    // setup function called once to generate initial player locations
    // note the dance caller does not know any player-player relationships, so order doesn't really matter in the Point[] you return. Just make sure your player is consistent with the indexing

    public Point[] generate_starting_locations() {

        Point[] L  = new Point [d];
        for (int i = 0; i < d; i++) {
            L[i] = snake[i];
            snakeDancers.add(i);
        }
        destinations = new Point[d];
        for (int i = 0; i < d; i++) {
            destinations[i] = L[i];
        }
        return L;
    }

    private int play_counter = 0;

    private int find_lowest_scoring_dancer(int[] scores) {
        int min_val = scores[0];
        int min_index = 0;
        for(int i = 1; i < scores.length; ++i) {
            if (scores[i] < min_val) {
                min_index = i;
            }
        }
        return min_index;
    }

    private boolean is_lowest_scoring_dancer_scoring_bigly(int[] scores, int[] enjoyment_gained) {
        return enjoyment_gained[find_lowest_scoring_dancer(scores)] > 3;
    }

    private boolean is_lowest_scoring_dancer_not_scoring(int[] scores, int[] enjoyment_gained) {
        return enjoyment_gained[find_lowest_scoring_dancer(scores)] == 0;
    }

    // play function
    // dancers: array of locations of the dancers
    // scores: cumulative score of the dancers
    // partner_ids: index of the current dance partner. -1 if no dance partner
    // enjoyment_gained: integer amount (-5,0,3,4, or 6) of enjoyment gained in the most recent 6-second interval
    public Point[] play(Point[] dancers, int[] scores, int[] partner_ids, int[] enjoyment_gained) {
        Point[] instructions = new Point[d];
        for (int i = 0; i < d; i++) {
            instructions[i] = new Point(0, 0);
        }

        // time to dance and collect points and data
        if (mode == 0) {
            // DEBUG
            //System.out.println("Turncounter = " + play_counter);
            boolean foundBad = false;
            for (int i = 0; i < enjoyment_gained.length; i++) {
                if(enjoyment_gained[i] < 0) {
                    foundBad = true;
                    int currBad = snakeDancers.indexOf(i);
                    double badX = snake[currBad].x;
                    double badY = snake[currBad].y;
                    double actualX = dancers[i].x;
                    double actualY = dancers[i].y;
                    //System.out.println("Bad dancer: " + i + " at (" + badX + ", " + badY + "). ACTUAL: (" + actualX + ", " + actualY + ")");
                }
            }

            if (play_counter <= DANCE_TURNS || (LSD_OPTIMIZATION && is_lowest_scoring_dancer_scoring_bigly(scores, enjoyment_gained))) {
                play_counter += 1;
                return instructions;
            } else {
                play_counter = 0;
                mode = 1;
            }
            return instructions;
        }



        /* snake along and update destinations
           The snakeDancers List is a mapping from snake indicies to dancers. 
           The index of snakeDancers contains the dancer at that index in the 
           snake. 
           
           Example: snakeDancers.get(42) returns the index in snake where 
           the dancer id 42 is. If snakeDancers.get(42) returns 5, then 
           snake[5] contains the *position* on the grid where dancer 42 
           should be.
           
           The snakeDancers List must be updated every time dancers move
         */
        List<Integer> newSnakeDancers = new ArrayList<Integer>(); // new mapping for snake indexes to dancers
        int curr = snakeDancers.get(snakeDancers.size() - 1); // get the dancer id of the dancer at the end of snake
        destinations[curr] = snake[0]; // make that dancer move from end position of snake to beginning position of snake
        newSnakeDancers.add(curr); // begin updating snakeDancers to reflect the new snake mappings
        for (int i = 0; i < snakeDancers.size()-1; i++) {
            curr = snakeDancers.get(i);
            int nextPosInSnake = i + 1;
            destinations[curr] = snake[nextPosInSnake];
            newSnakeDancers.add(curr);
        }
        snakeDancers = newSnakeDancers;

        for (int i = 0; i < d; ++ i) {
            instructions[i] = getVector(destinations[i], dancers[i]);            
        }



        mode = 0; // dance next turn
        return instructions;        
    }
    
    private int total_enjoyment(int enjoyment_gained) {
	switch (enjoyment_gained) {
	case 3: return 60; // stranger
	case 4: return 200; // friend
	case 6: return 10800; // soulmate
	default: throw new IllegalArgumentException("Not dancing with anyone...");
	}	
    }

    /* creates a new array of points that consist of a snake of numDancers length
       Non-scaling, holds up to 2400 by incrementally turning PAIRS into short conveyors
       of 4, and distributing them throughout the snake. At 2400 every other pair is a conveyor
       For more than 2400 dancers, use createScalingSnake
     */
    private Point[] createSnake(int numDancers) {
        /* keep 40x40 grid, but replace pairs of dancers with a conveyor 
           of 4, evenly distributed
        */
        Point[] newSnake = new Point[numDancers];
        int numExcess = numDancers - 1600;
        int numPairsToReplace = numExcess / 2; // number of pair spots to replace with a conveyor of 4
        int conveyorInterval = 800 / numPairsToReplace; // interval to turn pairs into conveyors

        boolean outbound = true;
        int numOutbound = numDancers / 2;
        int x = 0, y = 0, dx = 0, dy = 1;
        int k = 0, pairX = 0, pairY = 0, pairCount = 0;
        int maxPairCount = (numDancers - numPairsToReplace * 2) / 2; // length of the snake, in pairs (conveyor counts as 1 pair)
        int replacedPairs = 0; // counter of replaced pairs
        int conveyorCounter = 0;
        int MAX_CONVEYOR = 2;
        for (int dancer = 0; dancer < numDancers; dancer++) {
            if (pairCount % conveyorInterval == 0
                && replacedPairs < numPairsToReplace) {
                // turns this pair spot into a conveyor spot
                // this only needs to happen on the outbound, when the snake returns it will fill the rest
                pairGrid[pairX][pairY] = 1;
                if (conveyorCounter == 0) {
                    replacedPairs++;
                }
            }
            
            if (pairGrid[pairX][pairY] > 0) {
                // this is a pair spot that should be used as a conveyor
                if (outbound) {
                    newSnake[dancer] = new Point(grid[x][y].x + conveyorCounter * CONVEYOR_GAP,
                                                 grid[x][y].y);
                }
                else {
                    newSnake[dancer] = new Point(grid[x][y].x - conveyorCounter * CONVEYOR_GAP,
                                                 grid[x][y].y);
                }
                conveyorCounter++;
                conveyorCounter %= MAX_CONVEYOR;

                if (outbound) {
                    // need to make the turnaround check, in case it happens in the middle of the conveyor
                    if (dancer == numOutbound - 1) {
                        outbound = false;
                        x += 1;
                        dy *= -1;
                        conveyorCounter = 0;
                        continue;
                    }
                }                
            }
            else {
                // normal pair spot, not a conveyor spot
                newSnake[dancer] = new Point(grid[x][y].x, grid[x][y].y);
            }

            if (conveyorCounter != 0) {
                // if still making a conveyor, don't snake along the grid
                continue;
            }

            /* As we iterate through the dancers, need to keep track of:
                - current x, y within the grid. This gives the Point location of the dancer.
                - current pairX, pairY within the pairGrid. This tells you whether that dancer
                  is in a conveyor spot or not
             */
            if (outbound) {
                if (dancer == numOutbound - 1) {
                    // last outbound dancer, start snaking back.
                    // note that pairGrid coords don't change
                    outbound = false;
                    x += 1;
                    dy *= -1;
                }
                else if (((y + dy) >= gridRows) || ((y + dy) < 0) ) {
                    // reached end of column, start next column
                    x += 2;
                    dy *= -1;
                    pairX++;
                    pairCount++;
                }
                else {
                    y += dy;
                    pairY += dy;
                    pairCount++;                    
                }
            }
            else { // inbound
                if (((y + dy) >= gridRows) || ((y + dy) < 0)) {
                    x -= 2;
                    dy *= -1;
                    pairX--;
                    pairCount++;
                }
                else {
                    y += dy;
                    pairY += dy;
                    pairCount++;
                }
            }

        } // end for loop through dancers
        return newSnake;
    }

    /* similar to createSnake, except scaling snake makes every other ROW a conveyor row
     */
    private Point[] createScalingSnake(int numDancers) {
        /* keep 40x40 grid, but replace pairs of dancers with a conveyor 
           of 4, evenly distributed
        */
        Point[] newSnake = new Point[numDancers];

        /* if numDancers > 4800, conveyors need to have more than 1 row
         */
        if (numDancers > 4800) {
            int numRowsInConveyor = 0;
            int numDanceRows = 0;
            for (int i = 2; i < 181; i++) { // 180 will result in 36k dancers
                double rowWidth = i * CONVEYOR_GAP + 2 * GRID_GAP; // width of each dancing row + i conveyors
                numDanceRows = (int)(room_side / rowWidth);
                int dancersPerRow = i * 200 + 40;
                int maxDancers = numDanceRows * dancersPerRow;
                if (maxDancers >= numDancers) {
                    numRowsInConveyor = i;
                    break;
                }
            }
            if (numRowsInConveyor == 0) {
                System.out.println("Too many dancers for createScalingSnake to handle!");
                System.exit(0);
            }

            /* remake grid, will look like below (example numRowsInConveyor == 3)
               o-o--o-o--o-o--o-o-- conveyor0 row 1
               -------------------- conveyor0 row 2
               -------------------- conveyor0 row 3
               * *  * *  * *  * *   dancer pairs row0
               o-o--o-o--o-o--o-o-- conveyor1 row 1    |
               -------------------- conveyor1 row 2    |--> this width will be 0.1 * 3 with 0.5 above/below
               -------------------- conveyor1 row 3    |
               * *  * *  * *  * *   dancer pairs row1            

               * = dancing dancer
               o = dancer spot on grid, but repurposed for conveyor, a conveyor dancer is here
               - = conveyor dancer
               note: width of each dancer spot on conveyor is 5 dancers

               pairGrid to keep track of conveyor spots will look like:
               [3][3][3][3][3] (3 is number of rows in conveyor)
               [0][0][0][0][0]
               [3][3][3][3][3]
               [0][0][0][0][0]
             */
            gridRows = numDanceRows * 2;
            grid = new Point[gridCols][gridRows];
            for (int i = 0; i < gridCols; i++) {
                for (int j = 0; j < gridRows; j++) {
                    double gridX = GRID_OFFSET_X + i * GRID_GAP;
                    if (i % 2 == 1) {
                        gridX -= DANCE_EPSILON;
                    }
                    double gridY = 0;
                    if ((j % 2) == 0) {
                        gridY = (j/2) * (GRID_GAP*2 + CONVEYOR_GAP * numRowsInConveyor) + GRID_OFFSET_Y;
                        grid[i][j] = new Point(gridX, gridY);
                    }
                    else {
                        gridY = (j/2) * (GRID_GAP * 2 + CONVEYOR_GAP * numRowsInConveyor) + GRID_OFFSET_Y;
                        gridY += GRID_GAP + CONVEYOR_GAP * numRowsInConveyor;
                        grid[i][j] = new Point(gridX, gridY);
                    }
                }
            }
            
            // recreate pairGrid and assign rows on pairGrid to be conveyor rows
            pairGridRows = gridRows;
            pairGrid = new int[pairGridCols][pairGridRows];
            for (int i = 0; i < pairGridCols; i++) {
                for (int j = 0; j < pairGridRows; j++) {
                    if (j % 2 == 0) {
                        pairGrid[i][j] = numRowsInConveyor;
                    }
                    else {
                        pairGrid[i][j] = 0;
                    }
                }
            }
        } // end if numDancers > 4800
        else {
            // single row conveyor
            // assign rows on pairGrid to be conveyor rows
            for (int i = 0; i < pairGridCols; i++) {
                for (int j = 0; j < pairGridRows; j++) {
                    if (j % 2 == 0) {
                        pairGrid[i][j] = 1;
                    }
                }
            }
        }
        
        /* Calculate length of the conveyors
           divide excess by number of spots for conveyors
           if doesn't evenly divide, get lengths of "longer" and "shorter" conveyors (longer will be +1 length)
           find out how many long vs short conveyors are needed
         */
        int numExcess = numDancers - (gridCols * gridRows)/2; // # dancers in excess of how many can be dancing at one time
        int numConveyorPairs = pairGridCols * pairGridRows; // total number of conveyor pair spots (on a 40x40 grid, this is 40x20)
        int conveyorLenShort = numExcess / numConveyorPairs; // divide and take ceil to get length of each short conveyor
        int conveyorLenLong = conveyorLenShort + 1;
        int excessShortConveyor = numExcess - conveyorLenShort * numConveyorPairs; // number remaining needed to distribute
        int numShortConveyors = numConveyorPairs - excessShortConveyor;
        int numLongConveyors = excessShortConveyor;

        boolean outbound = true;
        int numOutboundPairs = pairGridCols * pairGridRows;
        int x = 0, y = 0, dx = 0, dy = 1;
        int k = 0, pairX = 0, pairY = 0, pairCount = 0;
        int conveyorCounter = 0; // counter: dancers placed in current conveyor
        int placedInConveyor = 0; // counter: total dancers placed in all conveyors so far
        int currConveyor = 0; // counter: the number of conveyors placed so far
        int CONVEYOR_ROW_LEN = 5; // used when above 4800 dancers when conveyors can be multi row
        for (int dancer = 0; dancer < numDancers; dancer++) {
            if (pairGrid[pairX][pairY] > 0) {
                // this is a pair spot that should be used as a conveyor
                int conveyorY = conveyorCounter / CONVEYOR_ROW_LEN;
                int conveyorX = conveyorCounter % CONVEYOR_ROW_LEN;
                //newSnake[dancer] = new Point(grid[x][y].x + conveyorCounter * CONVEYOR_GAP, grid[x][y].y);
                newSnake[dancer] = new Point(grid[x][y].x + conveyorX * CONVEYOR_GAP,
                                             grid[x][y].y + conveyorY * CONVEYOR_GAP);
                conveyorCounter++;
                if (currConveyor < numLongConveyors) {
                    conveyorCounter %= conveyorLenLong;
                }
                else {
                    conveyorCounter %= conveyorLenShort;
                }
                
                if (conveyorCounter == 0) {
                    currConveyor++;
                }
            }
            else {
                // normal pair spot, not a conveyor spot
                newSnake[dancer] = new Point(grid[x][y].x, grid[x][y].y);
            }

            if (conveyorCounter != 0) {
                // if still making a conveyor, don't snake along the grid
                continue;
            }

            /* As we iterate through the dancers, need to keep track of:
                - current x, y within the grid. This gives the Point location of the dancer.
                - current pairX, pairY within the pairGrid. This tells you whether that dancer
                  is in a conveyor spot or not
             */
            if (outbound) {
                if (pairCount == numOutboundPairs - 1) {
                    // last outbound dancer, start snaking back.
                    // note that pairGrid coords don't change
                    outbound = false;
                    x += 1;
                    dy *= -1;
                }
                else if (((y + dy) >= gridRows) || ((y + dy) < 0) ) {
                    // reached end of column, start next column
                    x += 2;
                    dy *= -1;
                    pairX++;
                    pairCount++;
                }
                else {
                    y += dy;
                    pairY += dy;
                    pairCount++;                    
                }
            }
            else { // inbound
                if (((y + dy) >= gridRows) || ((y + dy) < 0)) {
                    x -= 2;
                    dy *= -1;
                    pairX--;
                    pairCount++;
                }
                else {
                    y += dy;
                    pairY += dy;
                    pairCount++;
                }
            }

        } // end for loop through dancers
        return newSnake;
    }

    
    private Point subtract(Point a, Point b) {
        return new Point(a.x - b.x, a.y - b.y);
    }

    private double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private Point getVector(Point a, Point b) {
        Point diff = new Point(a.x - b.x, a.y - b.y);
        double hypot = Math.hypot(diff.x, diff.y);
        if (hypot >= 1.999) {
            diff = new Point(diff.x/hypot * 1.999, diff.y/hypot * 1.999);
        }
        return diff;
    }    
}
