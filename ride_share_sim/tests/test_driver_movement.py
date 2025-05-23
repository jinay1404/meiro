import unittest
from ride_share_sim.model import RideShareModel
from ride_share_sim.agents import Rider, Driver, Vehicle

class TestDriverMovement(unittest.TestCase):

    def setUp(self):
        """Set up a default model for testing driver movement."""
        self.model = RideShareModel(width=10, height=10, n_drivers=0, n_riders=0, n_pois=2) # No initial agents

    def test_move_to_pickup(self):
        """Test driver movement towards a rider and pickup."""
        # Create Rider
        rider_id = self.model.next_id()
        rider = Rider(rider_id, self.model)
        rider_pos = (0, 2)
        rider_dest = (5, 5) # Rider's final destination
        rider.pos = rider_pos
        rider.destination = rider_dest
        rider.status = "assigned" # Rider is waiting for pickup
        self.model.grid.place_agent(rider, rider_pos)
        self.model.schedule.add(rider)

        # Create Driver
        driver_id = self.model.next_id()
        vehicle = Vehicle(vehicle_id=f"v_{driver_id}")
        driver = Driver(driver_id, self.model, vehicle)
        driver_start_pos = (0, 0)
        driver.pos = driver_start_pos
        driver.status = "picking_up"
        driver.assigned_rider = rider
        driver.target_pos = rider_pos # Target is rider's position
        self.model.grid.place_agent(driver, driver_start_pos)
        self.model.schedule.add(driver)

        # Step 1: Driver moves from (0,0) to (0,1)
        driver.step()
        self.assertEqual(driver.pos, (0, 1), "Driver should move to (0,1)")
        
        # Step 2: Driver moves from (0,1) to (0,2) (reaches rider)
        driver.step()
        self.assertEqual(driver.pos, (0, 2), "Driver should move to (0,2) and reach rider")
        
        # Assertions after reaching rider
        self.assertEqual(driver.status, "on_trip", "Driver status should be 'on_trip'")
        self.assertEqual(rider.status, "in_transit", "Rider status should be 'in_transit'")
        self.assertEqual(driver.target_pos, rider_dest, "Driver target should be rider's destination")
        self.assertEqual(rider.pos, driver.pos, "Rider should now be at the driver's position")


    def test_move_to_destination_and_drop_off(self):
        """Test driver movement with rider to destination and drop off."""
        # Create Rider
        rider_id = self.model.next_id()
        rider = Rider(rider_id, self.model)
        rider_start_pos = (0,0) # Rider starts at the same position as driver for this test
        rider_dest = (0, 2)
        rider.pos = rider_start_pos
        rider.destination = rider_dest
        rider.status = "in_transit" # Rider is already picked up
        rider.request_time = 0 # Dummy value
        rider.pickup_time = 1 # Dummy value, must be <= current time
        self.model.grid.place_agent(rider, rider_start_pos)
        self.model.schedule.add(rider)

        # Create Driver
        driver_id = self.model.next_id()
        vehicle = Vehicle(vehicle_id=f"v_{driver_id}")
        driver = Driver(driver_id, self.model, vehicle)
        driver.pos = rider_start_pos # Driver starts at the same position as rider
        driver.status = "on_trip"
        driver.assigned_rider = rider
        driver.target_pos = rider_dest
        self.model.grid.place_agent(driver, rider_start_pos)
        self.model.schedule.add(driver)
        
        self.model.schedule.steps = 1 # Set current time for accurate trip duration calculation

        # Step 1: Driver and Rider move from (0,0) to (0,1)
        driver.step() 
        self.assertEqual(driver.pos, (0, 1), "Driver should move to (0,1)")
        self.assertEqual(rider.pos, (0, 1), "Rider should move with driver to (0,1)")

        # Step 2: Driver and Rider move from (0,1) to (0,2) (reaches destination)
        driver.step()
        self.assertEqual(driver.pos, (0, 2), "Driver should reach destination (0,2)")
        # Rider's position should also be (0,2), but rider is removed from grid upon arrival.
        # self.assertEqual(rider.pos, (0,2)) # This check is tricky as rider is removed.

        # Assertions after reaching destination
        self.assertEqual(driver.status, "idle", "Driver status should be 'idle'")
        self.assertIsNone(driver.assigned_rider, "Driver should not have an assigned rider")
        self.assertIsNone(driver.target_pos, "Driver target_pos should be None")
        
        # Rider status is internally set to "arrived" by driver, then rider is removed.
        # We can check model stats if needed, or that rider is no longer in schedule.
        self.assertNotIn(rider, self.model.schedule.agents, "Rider should be removed from schedule after drop-off")
        
        # Check if trip stats were updated (basic check)
        self.assertEqual(self.model.completed_trips, 1)


if __name__ == '__main__':
    unittest.main()
