/*
 *  Copyright (C) 2020 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package we.stats;

import we.stats.circuitbreaker.CircuitBreaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 
 * @author Francis Dong
 *
 */
public class TimeSlot {

	/**
	 * Time slot start timestamp as ID
	 */
	private long id;

	/**
	 * Request counter
	 */
	private AtomicLong counter = new AtomicLong();

	/**
	 * Error request counter
	 */
	private AtomicLong errors = new AtomicLong();

	/**
	 * Minimum response time
	 */
	private long min = Long.MAX_VALUE;

	/**
	 * Maximum response time
	 */
	private long max = Long.MIN_VALUE;

	/**
	 * Total response time
	 */
	private AtomicLong totalRt = new AtomicLong(0);
	
	/**
	 * Completed Request counter
	 */
	private AtomicLong compReqs = new AtomicLong();
	

	/**
	 * Peak concurrent requests
	 */
	private long peakConcurrentRequests;

	/**
	 * Block requests <br/>
	 */
	private AtomicLong blockRequests = new AtomicLong(0);
	
	/**
	 * Total block requests of the resource and its underlying resources <br/>
	 */
	private AtomicLong totalBlockRequests = new AtomicLong(0);


	private AtomicReference<CircuitBreaker.State> circuitBreakState   = new AtomicReference<>(CircuitBreaker.State.CLOSED);

	private AtomicLong                            circuitBreakNum     = new AtomicLong(0);

	private AtomicLong                            gradualResumeNum    = new AtomicLong(0);

	private AtomicInteger                         resumeTrafficFactor = new AtomicInteger(1);

	private AtomicLong                            gradualRejectNum    = new AtomicLong(0);

	private AtomicInteger                         _2xxStatusCount     = new AtomicInteger(0);

	private AtomicInteger                         _4xxStatusCount     = new AtomicInteger(0);

	private AtomicInteger                         _5xxStatusCount     = new AtomicInteger(0);

	private AtomicInteger                         _504StatusCount     = new AtomicInteger(0);

	public AtomicInteger get2xxStatusCount() {
		return _2xxStatusCount;
	}

	public AtomicInteger get4xxStatusCount() {
		return _4xxStatusCount;
	}

	public AtomicInteger get5xxStatusCount() {
		return _5xxStatusCount;
	}

	public AtomicInteger get504StatusCount() {
		return _504StatusCount;
	}

	public AtomicReference<CircuitBreaker.State> getCircuitBreakState() {
		return circuitBreakState;
	}

	public AtomicLong getCircuitBreakNum() {
		return circuitBreakNum;
	}

	public AtomicLong getGradualResumeNum() {
		return gradualResumeNum;
	}

	public AtomicInteger getResumeTrafficFactor() {
		return resumeTrafficFactor;
	}

	public AtomicLong getGradualRejectNum() {
		return gradualRejectNum;
	}


	public TimeSlot(long id) {
		this.id = id;
	}

	public long getId() {
		return id;
	}

	/**
	 * Add request to time slot
	 * 
	 */
	public void incr() {
		counter.incrementAndGet();
	}

	/**
	 * Add request RT information to time slot
	 * 
	 * @param rt
	 * @param isSuccess Whether the request is success or not
	 */
	public synchronized void addRequestRT(long rt, boolean isSuccess) {
		totalRt.addAndGet(rt);
		compReqs.incrementAndGet();
		if (!isSuccess) {
			errors.incrementAndGet();
		}
		min = rt < min ? rt : min;
		max = rt > max ? rt : max;
	}

	/**
	 * Update peak concurrent requests of this time slot
	 * 
	 * @param concurrentRequests Current concurrent requests
	 */
	public synchronized void updatePeakConcurrentReqeusts(long concurrentRequests) {
		peakConcurrentRequests = concurrentRequests > peakConcurrentRequests ? concurrentRequests
				: peakConcurrentRequests;
	}

	public void setId(long id) {
		this.id = id;
	}

	public AtomicLong getCounter() {
		return counter;
	}

	public void setCounter(AtomicLong counter) {
		this.counter = counter;
	}

	public long getMin() {
		return min;
	}

	public void setMin(long min) {
		this.min = min;
	}

	public long getMax() {
		return max;
	}

	public void setMax(long max) {
		this.max = max;
	}

	public AtomicLong getTotalRt() {
		return totalRt;
	}

	public void setTotalRt(AtomicLong totalRt) {
		this.totalRt = totalRt;
	}

	public long getPeakConcurrentRequests() {
		return peakConcurrentRequests;
	}

	public void setPeakConcurrentRequests(long peakConcurrentRequests) {
		this.peakConcurrentRequests = peakConcurrentRequests;
	}

	public AtomicLong getErrors() {
		return errors;
	}

	public void setErrors(AtomicLong errors) {
		this.errors = errors;
	}

	public AtomicLong getBlockRequests() {
		return blockRequests;
	}

	public void setBlockRequests(AtomicLong blockRequests) {
		this.blockRequests = blockRequests;
	}

	public AtomicLong getCompReqs() {
		return compReqs;
	}

	public void setCompReqs(AtomicLong compReqs) {
		this.compReqs = compReqs;
	}

	public AtomicLong getTotalBlockRequests() {
		return totalBlockRequests;
	}

	public void setTotalBlockRequests(AtomicLong totalBlockRequests) {
		this.totalBlockRequests = totalBlockRequests;
	}

}
