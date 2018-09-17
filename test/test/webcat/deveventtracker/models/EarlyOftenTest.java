package test.webcat.deveventtracker.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.webcat.deveventtracker.models.metrics.EarlyOften;

/**
 * @author Ayaan Kazerouni
 * @version 2018-09-13
 */
public class EarlyOftenTest {
	private EarlyOften earlyOften;
	private Map<String, Integer> batchProcessed;
	
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeEach
	public void setUp() throws Exception {
		this.earlyOften = new EarlyOften();
		this.batchProcessed = new HashMap<String, Integer>();
		this.batchProcessed.put("totalEdits", 400);
		this.batchProcessed.put("totalWeightedEdits", 1200);
	}
	
	@Test
	@DisplayName("update; simple test")
	public void testSimple() {
		this.earlyOften.update(this.batchProcessed);
		assertEquals(3, this.earlyOften.getScore());
	}
}