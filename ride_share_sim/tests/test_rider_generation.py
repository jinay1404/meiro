import unittest
from ride_share_sim.model import RideShareModel
from ride_share_sim.agents import Rider

class TestRiderGeneration(unittest.TestCase):

    def setUp(self):
        """Set up a default model for testing."""
        self.model = RideShareModel(width=10, height=10, n_drivers=1, n_pois=5, n_riders=0) # n_riders=0 initially
        # Ensure there are enough POIs for tests that require sampling 2
        if len(self.model.grid.pois) < 2:
             # Add more POIs if needed for specific tests, though model setup should handle this
             for i in range(len(self.model.grid.pois), 5):
                x,y = self.model.random.randrange(0,10), self.model.random.randrange(0,10)
                if (x,y) not in self.model.grid.pois:
                    self.model.grid.pois.append((x,y))


    def test_rider_request_generation(self):
        """Test if new rider requests are generated with correct attributes."""
        self.model.prob_new_rider_request = 1.0  # Ensure a rider is generated
        
        initial_rider_count = len([agent for agent in self.model.schedule.agents if isinstance(agent, Rider)])
        
        # Run step multiple times to ensure rider generation if POIs are sufficient
        for _ in range(5): # Run a few steps in case POI selection fails initially
            if len(self.model.grid.pois) >=2:
                 self.model.step()
                 break # Assume one step is enough if POIs are fine
            else: # Try to add more POIs if model didn't create enough distinct ones
                for i in range(len(self.model.grid.pois), 5):
                    x,y = self.model.random.randrange(0,10), self.model.random.randrange(0,10)
                    if (x,y) not in self.model.grid.pois:
                         self.model.grid.pois.append((x,y))


        riders = [agent for agent in self.model.schedule.agents if isinstance(agent, Rider)]
        new_rider_count = len(riders)

        if len(self.model.grid.pois) < 2:
            self.assertEqual(initial_rider_count, new_rider_count, "No riders should be generated if less than 2 POIs")
            return # Skip further checks if no riders can be generated

        self.assertTrue(new_rider_count > initial_rider_count, "New rider should be generated")
        
        # Assuming the last added rider is the new one for property checks
        # This might be fragile if multiple riders are generated in one step (not current logic)
        if riders and new_rider_count > initial_rider_count: # Ensure we are checking a newly generated rider
            new_rider = None
            # Find a rider that wasn't in the initial list (if any were there)
            # This is a bit complex if we don't have initial riders' IDs.
            # Simplest is to assume the last one added if only one step is taken for generation.
            # For this test, initial_rider_count is 0.
            new_rider = riders[-1] 

            self.assertIn(new_rider.status, ["requesting", "assigned", "in_transit"],
                          f"New rider status should be 'requesting', 'assigned', or 'in_transit', but was {new_rider.status}")
            self.assertIsNotNone(new_rider.destination, "New rider should have a destination")
            self.assertIsNotNone(new_rider.request_time, "New rider should have a request_time")
            self.assertIsNotNone(new_rider.pos, "New rider should have a position")
            
            self.assertTrue(self.model.grid.is_within_bounds(new_rider.pos), "Rider start position is out of bounds")
            self.assertTrue(self.model.grid.is_within_bounds(new_rider.destination), "Rider destination is out of bounds")
            self.assertNotEqual(new_rider.pos, new_rider.destination, "Rider start and destination should be different")

            # Check if start and destination are from POIs (current logic)
            self.assertIn(new_rider.pos, self.model.grid.pois, "Rider start position should be a POI")
            self.assertIn(new_rider.destination, self.model.grid.pois, "Rider destination should be a POI")


if __name__ == '__main__':
    unittest.main()
