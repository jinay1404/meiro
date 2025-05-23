import numpy as np
from mesa import Model, Agent
from mesa.time import RandomActivation
from mesa.space import MultiGrid
from ride_share_sim.agents import Driver, Rider, Vehicle # Adjusted for package structure
from .utils import manhattan_distance # Adjusted for package structure

class OperatingRegion(MultiGrid):
    def __init__(self, width, height, n_pois):
        super().__init__(width, height, torus=False)
        self.width = width
        self.height = height
        self.pois = self._generate_pois(n_pois)

    def _generate_pois(self, n_pois):
        pois = []
        for _ in range(n_pois):
            x, y = np.random.randint(0, self.width), np.random.randint(0, self.height)
            new_poi = (x,y)
            if new_poi not in pois: # Avoid duplicate POI locations
                 pois.append(new_poi)
        return pois

    def is_within_bounds(self, pos):
        x, y = pos
        return 0 <= x < self.width and 0 <= y < self.height
    
    def find_empty(self): # Helper to find a random empty cell, if needed
        if hasattr(self.grid, 'exists_empty_cells') and self.grid.exists_empty_cells(): # Check if method exists
            empty_cells = [pos for x in range(self.grid.width) for y in range(self.grid.height) if self.grid.is_cell_empty((x,y))]
            if empty_cells:
                return self.random.choice(empty_cells)
        # Fallback if exists_empty_cells is not available or no empty cells
        all_cells = [(x, y) for x in range(self.grid.width) for y in range(self.grid.height)]
        self.random.shuffle(all_cells)
        for pos in all_cells:
            if self.grid.is_cell_empty(pos):
                return pos
        return None


class RideShareModel(Model):
    def __init__(self, width=100, height=100, n_drivers=None, n_riders=None, n_pois=10): # n_riders is unused for creation
        super().__init__()
        self.prob_new_rider_request = 0.3 
        
        # Data collection attributes
        self.completed_trips = 0
        self.total_wait_time = 0 
        self.total_trip_duration = 0
        
        self.grid = OperatingRegion(width, height, n_pois)
        self.schedule = RandomActivation(self)

        # Create drivers
        self.n_drivers = n_drivers or np.random.randint(3, 10)
        for _ in range(self.n_drivers): # Use _ if i is not directly used for vehicle ID
            vehicle_id = self.next_id() # Use next_id for unique vehicle IDs too
            vehicle = Vehicle(vehicle_id) 
            driver_id = self.next_id() 
            driver = Driver(driver_id, self, vehicle)
            
            start_pos = None
            if self.grid.pois:
                start_pos = self.random.choice(self.grid.pois)
            else: # Fallback if no POIs
                start_pos = self.grid.find_empty()

            if start_pos:
                self.grid.place_agent(driver, start_pos)
                driver.pos = start_pos 
                self.schedule.add(driver)
            else:
                print(f"Warning: Could not place driver {driver_id}. No suitable start_pos found.")

    def step(self):
        # Generate new rider requests
        if self.random.random() < self.prob_new_rider_request and self.grid.pois and len(self.grid.pois) >= 2:
            start_pos, dest_pos = self.random.sample(self.grid.pois, 2) 
            rider_unique_id = self.next_id() 
            new_rider = Rider(rider_unique_id, self)
            new_rider.status = "requesting" 
            new_rider.request_time = self.schedule.steps
            new_rider.destination = dest_pos
            
            self.grid.place_agent(new_rider, start_pos) 
            self.schedule.add(new_rider)

        # Match riders to drivers
        self.match_riders()

        self.schedule.step() # Advance all agents

    def match_riders(self):
        requesting_riders = [r for r in self.schedule.agents if isinstance(r, Rider) and r.status == "requesting"]
        idle_drivers = [d for d in self.schedule.agents if isinstance(d, Driver) and d.status == "idle"]

        if not idle_drivers or not requesting_riders: 
            return

        for rider in requesting_riders:
            if not idle_drivers: 
                break

            closest_driver = None
            min_distance = float('inf')

            if rider.pos is None: 
                print(f"Warning: Rider {rider.unique_id} has no position. Skipping matching.")
                continue

            for driver in idle_drivers:
                if driver.pos is None: 
                    print(f"Warning: Driver {driver.unique_id} has no position. Skipping for matching.")
                    continue
                
                distance = manhattan_distance(rider.pos, driver.pos)
                if distance < min_distance:
                    min_distance = distance
                    closest_driver = driver
            
            if closest_driver:
                closest_driver.assigned_rider = rider
                closest_driver.status = "picking_up"
                closest_driver.target_pos = rider.pos 
                rider.status = "assigned"
                idle_drivers.remove(closest_driver)
