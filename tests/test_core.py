import unittest
try:
    from input_control import InputController
except ModuleNotFoundError:
    from galaxy_voice_codex.input_control import InputController
class CoreTests(unittest.TestCase):
    def test_not_ready_without_window(self): self.assertFalse(InputController(['Visual Studio Code']).is_ready())
if __name__=='__main__': unittest.main()
