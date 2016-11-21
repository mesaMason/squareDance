package sqdance.g1;

import sqdance.sim.Point;

import java.io.*;
import java.util.*;

public class SnakePlayer implements sqdance.sim.Player {

    // globals
    private Point[][] grid;
    private int gridCols = 0; // number of columns (must be even number...)
    private int gridRows = 0; // number of pairs per column
    private Point[] snake; // size = num dancers in snake - 1 (stationary dancer isn't in snake)
    private int snakeMovingLen; // number of dancers that move in the snake (size of snake)
    private List<Integer> snakeDancers; // holds dancer ids of those in the snake
    private int stationaryDancer; // dancer id of the stationary one
    
    // constants
    private final double GRID_GAP = 0.5001; // distance between grid points
    private final double GRID_OFFSET_X = 0.4; // offset of entire grid from 0,0
    private final double GRID_OFFSET_Y = 0.4;
    
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
        gridCols = (int) side;
        if ((gridCols % 2) == 1) {
            gridCols--;
        }
        gridRows = (int) side;
        grid = new Point[gridCols][gridRows];
        for (int i = 0; i < gridCols; i++) {
            for (int j = 0; j < gridRows; j++) {
                double gridX = GRID_OFFSET_X + i * GRID_GAP;
                double gridY = GRID_OFFSET_Y + j * GRID_GAP;
                if ((i % 2) == 1) {
                    gridX -= 0.00001;
                }
                grid[i][j] = new Point(gridX, gridY);
            }
        }

        snakeDancers = new ArrayList<Integer>();
        
        // create snake positions
        snake = createSnake(d);
    }

    // setup function called once to generate initial player locations
    // note the dance caller does not know any player-player relationships, so order doesn't really matter in the Point[] you return. Just make sure your player is consistent with the indexing

    public Point[] generate_starting_locations() {
        Point[] L  = new Point [d];
        stationaryDancer = 0;
        L[stationaryDancer] = grid[0][0]; // stationary dancer at 0,0
        for (int i = 1; i < d; i++) {
            L[i] = snake[i-1];
            snakeDancers.add(i);
        }
        System.out.println("Last dancer: " + snakeDancers.get(snakeDancers.size()-1));
        return L;
    }

    // play function
    // dancers: array of locations of the dancers
    // scores: cumulative score of the dancers
    // partner_ids: index of the current dance partner. -1 if no dance partner
    // enjoyment_gained: integer amount (-5,0,3,4, or 6) of enjoyment gained in the most recent 6-second interval
    public Point[] play(Point[] dancers, int[] scores, int[] partner_ids, int[] enjoyment_gained) {
        Point[] instructions = new Point[d];
        Point[] destinations = new Point[d];
        for (int i = 0; i < d; i++) {
            instructions[i] = new Point(0, 0);
            destinations[i] = dancers[i];
        }

        // snake along
        List<Integer> newSnakeDancers = new ArrayList<Integer>();
        int curr = snakeDancers.get(snakeDancers.size() - 1); // move last one to beginning
        destinations[curr] = snake[0];
        newSnakeDancers.add(curr);
        for (int i = 0; i < snakeDancers.size()-1; i++) {
            curr = snakeDancers.get(i);
            int nextPosInSnake = (i + 1) % snakeDancers.size();
            destinations[curr] = snake[nextPosInSnake];
            newSnakeDancers.add(curr);
        }
        snakeDancers = newSnakeDancers;

        for (int i = 0; i < d; ++ i) {
            instructions[i] = direction(subtract(destinations[i], dancers[i]));
        }
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

    // creates a snake of numDancers-1 length
    private Point[] createSnake(int numDancers) {
        snakeMovingLen = numDancers-1;
        Point[] newSnake = new Point[snakeMovingLen];
        int numOutbound = snakeMovingLen / 2; // numDancers - 1 is odd

        boolean outbound = true;
        int x = 0, y = 1, dx = 0, dy = 1;
        for (int dancer = 0; dancer < snakeMovingLen; dancer++) {
            newSnake[dancer] = new Point(grid[x][y].x, grid[x][y].y);
            if (outbound) {
                if (dancer == numOutbound - 1) {
                    // last outbound dancer, start snaking back
                    outbound = false;
                    x += 1;
                    dy *= -1;
                }
                else if (((y + dy) >= gridRows) || ((y + dy) < 0) ) {
                    // reached end of column, start next column
                    x += 2;
                    dy *= -1;
                }
                else {
                    y += dy;
                }
            }
            else { // inbound
                if (((y + dy) >= gridRows) || ((y + dy) < 0)) {
                    x -= 2;
                    dy *= -1;
                }
                else {
                    y += dy;
                }
            }
        } // end for loop through dancers
        System.out.println("First pos in snake: (" + newSnake[0].x + ", " + newSnake[0].y + ")");
        System.out.println("Last pos in snake: (" + newSnake[snakeMovingLen-1].x + ", " + newSnake[snakeMovingLen-1].y + ")");
        return newSnake;
    }

    private Point subtract(Point a, Point b) {
        return new Point(a.x - b.x, a.y - b.y);
    }

    private Point direction(Point a) {
        double l = Math.hypot(a.x, a.y);
        if (l <= 1 + 1e-8) return a;
        else return new Point(a.x / l, a.y / l);
    }
    
}
