import unittest
from ride_share_sim.model import RideShareModel
from ride_share_sim.agents import Rider, Driver, Vehicle
from ride_share_sim.utils import manhattan_distance # Though not directly used in assertions, good for context

class TestDriverRiderMatching(unittest.TestCase):

    def setUp(self):
        """Set up a default model for testing matching."""
        # n_pois is set to 0 as POIs are not strictly needed for these specific matching tests
        # and it simplifies setup. If POIs were used for agent placement, this would change.
        self.model = RideShareModel(width=10, height=10, n_drivers=0, n_riders=0, n_pois=2)


    def test_basic_matching(self):
        """Test if a single idle driver is matched with a single requesting rider."""
        # Manually create one Rider
        rider_id = self.model.next_id()
        rider = Rider(rider_id, self.model)
        rider_pos = (2, 2)
        self.model.grid.place_agent(rider, rider_pos)
        rider.pos = rider_pos # Ensure agent's pos attribute is also set
        rider.status = "requesting"
        rider.destination = (8,8) # Needs a destination
        self.model.schedule.add(rider)

        # Manually create one Driver
        driver_id = self.model.next_id()
        vehicle = Vehicle(vehicle_id=f"v_{driver_id}")
        driver = Driver(driver_id, self.model, vehicle)
        driver_pos = (1, 1)
        self.model.grid.place_agent(driver, driver_pos)
        driver.pos = driver_pos # Ensure agent's pos attribute
        driver.status = "idle"
        self.model.schedule.add(driver)
        
        # Call the model's matching method
        self.model.match_riders()

        self.assertEqual(rider.status, "assigned", "Rider status should change to 'assigned'")
        self.assertEqual(driver.status, "picking_up", "Driver status should change to 'picking_up'")
        self.assertEqual(driver.assigned_rider, rider, "Driver should be assigned to the rider")
        self.assertEqual(driver.target_pos, rider_pos, "Driver's target should be the rider's position")

    def test_nearest_driver_matching(self):
        """Test if the nearest idle driver is matched when multiple drivers are available."""
        # Manually create one Rider
        rider_id = self.model.next_id()
        rider = Rider(rider_id, self.model)
        rider_pos = (5, 5)
        self.model.grid.place_agent(rider, rider_pos)
        rider.pos = rider_pos
        rider.status = "requesting"
        rider.destination = (9,9)
        self.model.schedule.add(rider)

        # Manually create two Drivers
        vehicle1 = Vehicle(vehicle_id="v1")
        driver1_id = self.model.next_id()
        driver1 = Driver(driver1_id, self.model, vehicle1) # Closer driver
        driver1_pos = (4, 5)
        self.model.grid.place_agent(driver1, driver1_pos)
        driver1.pos = driver1_pos
        driver1.status = "idle"
        self.model.schedule.add(driver1)

        vehicle2 = Vehicle(vehicle_id="v2")
        driver2_id = self.model.next_id()
        driver2 = Driver(driver2_id, self.model, vehicle2) # Further driver
        driver2_pos = (1, 1)
        self.model.grid.place_agent(driver2, driver2_pos)
        driver2.pos = driver2_pos
        driver2.status = "idle"
        self.model.schedule.add(driver2)
        
        # Call the model's matching method
        self.model.match_riders()

        self.assertEqual(driver1.status, "picking_up", "Closer driver (Driver1) should be 'picking_up'")
        self.assertEqual(driver1.assigned_rider, rider, "Closer driver (Driver1) should be assigned to the rider")
        self.assertEqual(driver2.status, "idle", "Further driver (Driver2) should remain 'idle'")
        self.assertIsNone(driver2.assigned_rider, "Further driver (Driver2) should not have an assigned rider")

if __name__ == '__main__':
    unittest.main()
