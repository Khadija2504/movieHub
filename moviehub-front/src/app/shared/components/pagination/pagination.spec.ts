import { pageWindow } from './pagination';

describe('pageWindow', () => {
  it('shows all pages when there are few', () => {
    expect(pageWindow(2, 3)).toEqual([1, 2, 3]);
  });

  it('adds gaps around the current page', () => {
    expect(pageWindow(10, 20)).toEqual([1, null, 9, 10, 11, null, 20]);
  });

  it('handles the edges', () => {
    expect(pageWindow(1, 20)).toEqual([1, 2, null, 20]);
    expect(pageWindow(20, 20)).toEqual([1, null, 19, 20]);
  });
});
