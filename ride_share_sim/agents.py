import numpy as np
from mesa import Agent
from mesa.space import MultiGrid

class Vehicle:
    def __init__(self, vehicle_id, speed=1, capacity=1):
        self.vehicle_id = vehicle_id
        self.speed = speed
        self.capacity = capacity

class Driver(Agent):
    def __init__(self, unique_id, model, vehicle, search_strategy="random", insight_prob=0.5):
        # super().__init__(unique_id, model) # Using direct assignment as per environment
        self.unique_id = unique_id
        self.model = model
        self.pos = None
        self.state = "active"  # Original attribute, might be redundant with new 'status'
        self.vehicle = vehicle
        self.search_strategy = search_strategy # For idle movement
        self.insight_prob = insight_prob

        self.waiting_time = 0
        self.working_time = 0
        
        self.status = "idle"  # "idle", "picking_up", "on_trip"
        self.assigned_rider = None
        self.target_pos = None

    def move_towards(self, target_pos):
        if not target_pos or self.pos == target_pos:
            return

        current_x, current_y = self.pos
        target_x, target_y = target_pos

        # Try to move in x direction
        if current_x != target_x:
            next_x = current_x + (1 if target_x > current_x else -1)
            next_pos = (next_x, current_y)
            
            cell_contents = self.model.grid.get_cell_list_contents([next_pos])
            can_move = not cell_contents or \
                       (self.assigned_rider and len(cell_contents) == 1 and self.assigned_rider in cell_contents)

            if self.model.grid.is_within_bounds(next_pos) and can_move:
                self.model.grid.move_agent(self, next_pos)
                if self.status == "on_trip" and self.assigned_rider: 
                    self.model.grid.move_agent(self.assigned_rider, next_pos)
                return

        # If no move in x or x-move was not possible, try to move in y direction
        if current_y != target_y:
            next_y = current_y + (1 if target_y > current_y else -1)
            next_pos = (current_x, next_y)

            cell_contents = self.model.grid.get_cell_list_contents([next_pos])
            can_move = not cell_contents or \
                       (self.assigned_rider and len(cell_contents) == 1 and self.assigned_rider in cell_contents)
            
            if self.model.grid.is_within_bounds(next_pos) and can_move:
                self.model.grid.move_agent(self, next_pos)
                if self.status == "on_trip" and self.assigned_rider: 
                    self.model.grid.move_agent(self.assigned_rider, next_pos)
                return
    
    def step(self):
        if self.status == "idle":
            self.waiting_time += 1
            if self.search_strategy == "random":
                self.random_move()
            elif self.search_strategy == "poi":
                self.move_to_poi()
            else:
                self.random_move() 
        
        elif self.status == "picking_up":
            self.working_time += 1
            if self.target_pos and self.assigned_rider:
                if self.pos != self.target_pos: # If not at target
                    self.move_towards(self.target_pos) # Move first
                
                # Re-check position after move_towards for immediate status update
                if self.pos == self.target_pos: # Now check if target reached
                    self.status = "on_trip"
                    self.target_pos = self.assigned_rider.destination
                    self.assigned_rider.status = "in_transit"
                    self.assigned_rider.pickup_time = self.model.schedule.steps 
            else: 
                # If no target or no assigned rider, something is wrong, go idle.
                self.status = "idle"
                self.target_pos = None
                self.assigned_rider = None

        elif self.status == "on_trip":
            self.working_time += 1
            if self.target_pos and self.assigned_rider:
                if self.pos != self.target_pos: # If not at target
                    self.move_towards(self.target_pos) # Move first
                
                # Re-check position after move_towards for immediate status update
                if self.pos == self.target_pos: # Now check if target reached
                    self.assigned_rider.status = "arrived"
                    
                    current_step = self.model.schedule.steps
                    if self.assigned_rider.pickup_time is not None and \
                       self.assigned_rider.request_time is not None:
                        wait_time = self.assigned_rider.pickup_time - self.assigned_rider.request_time
                        trip_duration = current_step - self.assigned_rider.pickup_time
                        
                        self.model.total_wait_time += wait_time
                        self.model.total_trip_duration += trip_duration
                        self.model.completed_trips += 1
                    else:
                        print(f"Warning: Rider {self.assigned_rider.unique_id} missing time data for completed trip.")

                    self.model.grid.remove_agent(self.assigned_rider)
                    self.model.schedule.remove(self.assigned_rider)
                    
                    self.status = "idle"
                    self.target_pos = None
                    self.assigned_rider = None
            else:
                self.status = "idle"
                self.target_pos = None
                self.assigned_rider = None

    def random_move(self):
        possible_steps = self.model.grid.get_neighborhood(self.pos, moore=True, include_center=False)
        valid_steps = [p for p in possible_steps if self.model.grid.is_within_bounds(p) and self.model.grid.is_cell_empty(p)]
        if valid_steps:
            new_pos = self.random.choice(valid_steps)
            self.model.grid.move_agent(self, new_pos)

    def move_to_poi(self): 
        if not self.model.grid.pois:
            self.random_move() 
            return
        target_poi = self.random.choice(self.model.grid.pois)
        self.move_towards(target_poi)


class Rider(Agent):
    def __init__(self, unique_id, model):
        # super().__init__(unique_id, model) # Using direct assignment
        self.unique_id = unique_id
        self.model = model
        self.pos = None 
        self.destination = None
        self.request_time = None
        self.pickup_time = None # Time rider is picked up by a driver
        self.status = "waiting_for_request" # "waiting_for_request", "requesting", "assigned", "in_transit", "arrived"
        self.wait_time = 0 

    def step(self):
        if self.status == "requesting" or self.status == "assigned":
            self.wait_time += 1
        pass
